import os
import numpy as np
import cv2
import tensorflow as tf
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import Conv2D, MaxPooling2D, Flatten, Dense
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelBinarizer

# Funzione per caricare le immagini dal dataset
def load_images_from_folder(folder):
    images = []
    labels = []
    for label_folder in os.listdir(folder):
        label_path = os.path.join(folder, label_folder)
        if os.path.isdir(label_path):
            for filename in os.listdir(label_path):
                img_path = os.path.join(label_path, filename)
                img = cv2.imread(img_path)
                if img is not None:
                    images.append(cv2.resize(img, (128, 128)))
                    labels.append(label_folder)
    return images, labels

# Percorso del dataset
dataset_path = r'C:\Users\Fabio\Downloads\PlantReco\Dataset\color'

# Carica il dataset
images, labels = load_images_from_folder(dataset_path)

# Preprocessamento delle immagini
X = np.array(images) / 255.0  # Normalizza le immagini
y = np.array(labels)

# Suddividi il dataset in train e test
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

# Converti le etichette in formato one-hot
lb = LabelBinarizer()
y_train = lb.fit_transform(y_train)
y_test = lb.transform(y_test)

# Stampa i nomi delle classi
class_names = lb.classes_
print("Classi:", class_names)

# Salva i nomi delle classi in un file per riferimento futuro
with open('class_names.txt', 'w') as f:
    for class_name in class_names:
        f.write(f"{class_name}\n")

# Crea il modello CNN
model = Sequential([
    Conv2D(32, (3, 3), activation='relu', input_shape=(128, 128, 3)),
    MaxPooling2D(2, 2),
    Conv2D(64, (3, 3), activation='relu'),
    MaxPooling2D(2, 2),
    Flatten(),
    Dense(128, activation='relu'),
    Dense(len(class_names), activation='softmax')  # Numero di classi
])

# Compila il modello
model.compile(optimizer='adam', loss='categorical_crossentropy', metrics=['accuracy'])

# Addestra il modello
model.fit(X_train, y_train, epochs=10, validation_data=(X_test, y_test))

# Valuta il modello
loss, accuracy = model.evaluate(X_test, y_test)
print(f"Perdita: {loss}, Accuratezza: {accuracy}")

# Converti il modello in formato TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()

# Salva il modello TFLite
with open('plant_model.tflite', 'wb') as f:
    f.write(tflite_model)

print("Modello TFLite salvato come 'plant_model.tflite'")
