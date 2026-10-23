package com.example.mangrove.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.tensorflow.lite.Interpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class NoMangroveException : Exception("No mangrove found in this photo")
class ModelLoadException(why: String) : Exception(why)

/**
 * Steps: "AI tiles image" -> "Healthy / Unhealthy".
 * The photo is cut into 512 x 512 px tiles. The model reads each tile in 256 px pieces (Config.READ_SIZE), because it is
 * accurate at that scale; every piece is shrunk to the model's input size (224 x 224) and classified. The model has 3 outputs (Healthy, Non-mangrove, Unhealthy). Non-mangrove tiles
 * (water, mud, land) are skipped, so the % is only of mangrove pieces. Uses assets/mangrove_model.tflite (float32 RGB 0-255 in, 3 class scores out).
 * If the model cannot be loaded the app stops and says why (it never guesses from green pixels).
 */
class TileClassifier(private val ctx: Context) {
    private val tile = Config.TILE_SIZE
    private val input = Config.MODEL_INPUT

    private var loadError: String? = null

    // Reads the file with assets.open (works even if Android compressed the .tflite) instead of openFd.
    private val model: Interpreter? = try {
        val bytes = ctx.assets.open("mangrove_model.tflite").use { it.readBytes() }
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buf.put(bytes)
        buf.rewind()
        Interpreter(buf)
    } catch (e: Throwable) {
        loadError = "${e.javaClass.simpleName}: ${e.message}"
        android.util.Log.e("MangroveAI", "Model failed to load: $loadError")
        null
    }

    /**
     * Returns the share of Healthy / Unhealthy tiles, the Non-mangrove share and the tile map, and saves a
     * small preview photo to thumbFile. Throws if the photo cannot be read or has no mangrove.
     */
    suspend fun classify(uri: Uri, thumbFile: File, onProgress: (Float) -> Unit): TileResult = withContext(Dispatchers.Default) {
        if (model == null) throw ModelLoadException(loadError ?: "model not found in assets")
        val bmp = decode(uri) ?: throw IllegalStateException("Could not read the photo")
        val cols = bmp.width / tile
        val rows = bmp.height / tile
        if (cols == 0 || rows == 0) {                  // photo smaller than one tile: classify it whole
            val s = tileClass(bmp) ?: throw NoMangroveException()
            saveThumb(bmp, thumbFile)
            return@withContext if (s == Status.HEALTHY) TileResult(100, 0, 0, "H") else TileResult(0, 100, 0, "U")
        }
        val n = maxOf(1, tile / Config.READ_SIZE)      // pieces per side inside one tile (512 / 256 = 2)
        val ps = tile / n                              // piece size in px
        var pHealthy = 0                               // the Healthy % is counted over pieces (the scale the model is accurate at)
        var pUnhealthy = 0
        var done = 0
        val grid = StringBuilder()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                var th = 0
                var tu = 0
                for (py in 0 until n) for (px in 0 until n) {
                    val piece = Bitmap.createBitmap(bmp, c * tile + px * ps, r * tile + py * ps, ps, ps)
                    when (tileClass(piece)) {
                        Status.HEALTHY -> th++
                        Status.UNHEALTHY -> tu++
                        null -> {}                      // non-mangrove piece: not counted in the %
                    }
                }
                pHealthy += th
                pUnhealthy += tu
                // the tile map shows each 512 px tile by the majority of its pieces (a tie counts as Healthy)
                grid.append(if (th + tu == 0) 'N' else if (th >= tu) 'H' else 'U')
                done++
                onProgress(done / (rows * cols).toFloat())
                yield()
            }
            if (r < rows - 1) grid.append('/')
        }
        val total = pHealthy + pUnhealthy
        if (total == 0) throw NoMangroveException()
        val allPieces = rows * cols * n * n
        val h = (pHealthy * 100.0 / total).roundToInt()
        val nm = ((allPieces - total) * 100.0 / allPieces).roundToInt()
        saveThumb(Bitmap.createBitmap(bmp, 0, 0, cols * tile, rows * tile), thumbFile)   // exactly the tiled area
        TileResult(h, 100 - h, nm, grid.toString())
    }

    private fun saveThumb(b: Bitmap, f: File) {
        try {
            f.parentFile?.mkdirs()
            val k = 360f / maxOf(b.width, b.height)
            val t = if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
            f.outputStream().use { t.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        } catch (e: Exception) { }
    }

    private fun decode(uri: Uri): Bitmap? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val side = maxOf(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = maxOf(1, (side + Config.MAX_IMAGE_SIDE - 1) / Config.MAX_IMAGE_SIDE)
        }
        return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** null = Non-mangrove tile (water, mud, land). */
    private fun tileClass(t: Bitmap): Status? {
        val small = Bitmap.createScaledBitmap(t, input, input, true)    // piece (256 px) -> model size
        val px = IntArray(input * input)
        small.getPixels(px, 0, input, 0, 0, input, input)
        val m = model ?: throw ModelLoadException(loadError ?: "model not found in assets")
        val buf = ByteBuffer.allocateDirect(4 * input * input * 3).order(ByteOrder.nativeOrder())
        val k = Config.INPUT_SCALE
        for (p in px) {
            buf.putFloat(((p shr 16) and 0xFF) * k)
            buf.putFloat(((p shr 8) and 0xFF) * k)
            buf.putFloat((p and 0xFF) * k)
        }
        buf.rewind()
        val out = Array(1) { FloatArray(3) }
        m.run(buf, out)
        var best = 0
        for (i in 1 until 3) if (out[0][i] > out[0][best]) best = i
        // Logcat tag "MangroveAI": raw scores of every tile, to check the model's answers.
        android.util.Log.d("MangroveAI", "scores out0=%.2f out1=%.2f out2=%.2f -> %d".format(out[0][0], out[0][1], out[0][2], best))
        return when (best) {
            Config.CLASS_HEALTHY -> Status.HEALTHY
            Config.CLASS_UNHEALTHY -> Status.UNHEALTHY
            else -> null
        }
    }
}