import android.content.Context
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

object JsonReader {
    fun readJsonFromAsset(context: Context, fileName: String): String? {
        val json: String?
        try {
            val inputStream: InputStream = context.assets.open(fileName)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            json = String(buffer, StandardCharsets.UTF_8)
        } catch (ex: IOException) {
            ex.printStackTrace()
            return null
        }
        return json
    }
}
