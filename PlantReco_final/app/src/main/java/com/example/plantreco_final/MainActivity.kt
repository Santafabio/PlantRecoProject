package com.example.plantreco_final

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.nio.channels.FileChannel
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

// Classe principale che carica il modello
class MainActivity : ComponentActivity() {

    private var tflite: Interpreter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Controlla i permessi
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d("PlantRecoApp", "Permission not granted. Requesting permission.")
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 1)
        } else {
            Log.d("PlantRecoApp", "Permission granted. Loading model.")
            // Carica il modello
            loadModel()
        }

        // Usa setContent per la composizione della UI
        setContent {
            // Chiamiamo il composable giusto nel contesto composable
            PlantRecoApp(tflite)
        }
    }

    // Funzione per caricare il modello
    private fun loadModel() {
        try {
            val fileDescriptor: AssetFileDescriptor = assets.openFd("plant_model.tflite")
            Log.d("PlantRecoApp", "Model file descriptor obtained: ${fileDescriptor.fileDescriptor}")
            val inputStream = fileDescriptor.createInputStream()
            Log.d("PlantRecoApp", "Model input stream created")
            val fileChannel: FileChannel = inputStream.channel
            val byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileDescriptor.length)
            Log.d("PlantRecoApp", "Model byte buffer mapped with capacity: ${byteBuffer.capacity()}")
            tflite = Interpreter(byteBuffer)
            Log.d("PlantRecoApp", "Model loaded successfully")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("PlantRecoApp", "Model loading failed: ${e.message}")
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("PlantRecoApp", "Permission granted. Loading model...")
                try {
                    loadModel() // Prova a caricare il modello
                } catch (e: Exception) {
                    Log.e("PlantRecoApp", "Error loading model: ${e.message}")
                    e.printStackTrace() // Stampa stack trace per diagnosticare
                }
            } else {
                Log.d("PlantRecoApp", "Permission denied.")
                Toast.makeText(this, "Permesso negato. L'app non può funzionare senza i permessi.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Chiudi il modello quando l'Activity viene distrutta
    override fun onDestroy() {
        super.onDestroy()
        tflite?.close()
    }

    // Funzione per ottenere il modello tflite
    fun getTFLiteModel(): Interpreter? {
        return tflite
    }
}

// Funzione composable principale
@Composable
fun PlantRecoApp(tflite: Interpreter?) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var resultText by remember { mutableStateOf("Nessun risultato") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Ottieni il contesto dell'Activity
    val context = LocalContext.current

    // Creazione dell'ActivityResultLauncher per selezionare un'immagine
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            imageUri = uri // Aggiorna l'immagine selezionata
            uri?.let {
                val inputStream = context.contentResolver.openInputStream(it) // Ottieni l'InputStream dal URI
                inputStream?.let { stream ->
                    bitmap = BitmapFactory.decodeStream(stream) // Carica l'immagine in un Bitmap
                    Log.d("PlantRecoApp", "Image loaded successfully")
                    // Esegui il riconoscimento
                    bitmap?.let { image ->
                        val result = recognizeImage(image, tflite, context) // Passiamo anche il contesto
                        resultText = result
                    } ?: run {
                        Log.e("PlantRecoApp", "Bitmap is null")
                    }
                } ?: run {
                    Log.e("PlantRecoApp", "InputStream is null")
                }
            } ?: run {
                Log.e("PlantRecoApp", "Uri is null")
            }
        }
    )

    // Aggiungi un'immagine di sfondo
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visualizzazione dell'immagine selezionata
            imageUri?.let {
                Image(
                    painter = rememberAsyncImagePainter(model = it),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottone stilizzato
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
                modifier = Modifier.padding(8.dp)
            ) {
                Text(text = "Seleziona Immagine", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Risultato del riconoscimento in un Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

// Funzione di riconoscimento dell'immagine con il modello
fun recognizeImage(bitmap: Bitmap, tflite: Interpreter?, context: Context): String {
    val plantNames = arrayOf(
        "Apple___Apple_scab",
        "Apple___Black_rot",
        "Apple___Cedar_apple_rust",
        "Apple___healthy",
        "Blueberry___healthy",
        "Cherry_(including_sour)___Powdery_mildew",
        "Cherry_(including_sour)___healthy",
        "Corn_(maize)___Cercospora_leaf_spot Gray_leaf_spot",
        "Corn_(maize)___Common_rust_",
        "Corn_(maize)___Northern_Leaf_Blight",
        "Corn_(maize)___healthy",
        "Grape___Black_rot",
        "Grape___Esca_(Black_Measles)",
        "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)",
        "Grape___healthy",
        "Orange___Haunglongbing_(Citrus_greening)",
        "Peach___Bacterial_spot",
        "Peach___healthy",
        "Pepper,_bell___Bacterial_spot",
        "Pepper,_bell___healthy",
        "Potato___Early_blight",
        "Potato___Late_blight",
        "Potato___healthy",
        "Raspberry___healthy",
        "Soybean___healthy",
        "Squash___Powdery_mildew",
        "Strawberry___Leaf_scorch",
        "Strawberry___healthy",
        "Tomato___Bacterial_spot",
        "Tomato___Early_blight",
        "Tomato___Late_blight",
        "Tomato___Leaf_Mold",
        "Tomato___Septoria_leaf_spot",
        "Tomato___Spider_mites Two-spotted_spider_mite",
        "Tomato___Target_Spot",
        "Tomato___Tomato_Yellow_Leaf_Curl_Virus",
        "Tomato___Tomato_mosaic_virus",
        "Tomato___healthy"
    )

    // Inizializza l'input
    val input = Array(1) { FloatArray(128 * 128 * 3) }

    // Ridimensiona il bitmap
    val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
    val intValues = IntArray(128 * 128)
    resizedBitmap.getPixels(intValues, 0, 128, 0, 0, 128, 128)

    // Verifica la dimensione del bitmap ridimensionato
    Log.d("PlantRecoApp", "Resized bitmap loaded: ${resizedBitmap.width}x${resizedBitmap.height}")

    // Preprocessing dei pixel
    for (i in intValues.indices) {
        val pixel = intValues[i]
        input[0][i * 3] = (pixel shr 16 and 0xFF) / 255.0f  // Red
        input[0][i * 3 + 1] = (pixel shr 8 and 0xFF) / 255.0f  // Green
        input[0][i * 3 + 2] = (pixel and 0xFF) / 255.0f  // Blue
    }

    // Log dell'input preprocessato
    Log.d("PlantRecoApp", "Input for model: ${input.contentDeepToString()}")

    // Inizializza l'output con il numero corretto di classi
    val output = Array(1) { FloatArray(plantNames.size) }

    try {
        // Esegui l'inferenza
        tflite?.run(input, output)
        // Log dell'output
        Log.d("PlantRecoApp", "Output from model: ${output.contentDeepToString()}")
    } catch (e: Exception) {
        Log.e("PlantRecoApp", "Model inference failed: ${e.message}")
        return "Errore nel riconoscimento"
    }

    // Trova l'indice della classe con il punteggio più alto
    val maxIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1

    // Verifica se una pianta è stata riconosciuta
    if (maxIndex == -1 || output[0][maxIndex] < 0.5) {
        return "Pianta Riconosciuta: ${plantNames[maxIndex]}"
    }

    // Log della pianta riconosciuta
    Log.d("PlantRecoApp", "Plant recognized: ${plantNames[maxIndex]}")

    // Ritorna il nome della pianta riconosciuta
    return "Pianta Riconosciuta: ${plantNames[maxIndex]}"
}



