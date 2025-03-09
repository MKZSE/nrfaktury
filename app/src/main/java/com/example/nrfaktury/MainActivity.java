package com.example.nrfaktury;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private ImageView imgPreview;
    private ListView lstResults;
    private List<String> ocrResults = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private Bitmap capturedImage;

    // Przykładowe wzorce – dostosuj je do swoich wymagań
    private static final String INVOICE_PATTERN = "\\d{3,}"; // np. ciąg cyfr (min. 3 cyfry)
    private static final String DATE_PATTERN1 = "\\d{2}-\\d{2}-\\d{4}";
    private static final String DATE_PATTERN2 = "\\d{2}/\\d{2}/\\d{4}";
    private static final String DATE_PATTERN3 = "\\d{4}-\\d{2}-\\d{2}";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgPreview = findViewById(R.id.imgPreview);
        lstResults = findViewById(R.id.lstResults);
        Button btnOpenCamera = findViewById(R.id.btnOpenCamera);
        Button btnStartOcr = findViewById(R.id.btnStartOcr);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ocrResults);
        lstResults.setAdapter(adapter);

        ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        if (extras != null) {
                            capturedImage = (Bitmap) extras.get("data");
                            imgPreview.setImageBitmap(capturedImage);
                        }
                    }
                });

        btnOpenCamera.setOnClickListener(view -> {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(cameraIntent);
        });

        btnStartOcr.setOnClickListener(view -> {
            if (capturedImage != null) {
                processImageWithMlKit(capturedImage);
            } else {
                ocrResults.add("Najpierw wykonaj zdjęcie!");
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void processImageWithMlKit(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(resultText -> {
                    String recognizedText = resultText.getText().toUpperCase();

                    // Czyścimy poprzednie wyniki
                    ocrResults.clear();

                    // Definiujemy wzorzec na ciągi z co najmniej 2 slashami
                    Pattern slashPattern = Pattern.compile("[^\\s/]+(?:/[^\\s/]+){2,}");
                    Matcher matcher = slashPattern.matcher(recognizedText);

                    // Wypisujemy wszystkie dopasowania
                    boolean foundAny = false;
                    while (matcher.find()) {
                        foundAny = true;
                        String found = matcher.group();
                        ocrResults.add("Znaleziony numer: " + found);
                    }

                    if (!foundAny) {
                        ocrResults.add("Nie znaleziono ciągu z co najmniej dwoma ukośnikami.");
                    }

                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    ocrResults.clear();
                    ocrResults.add("Błąd przetwarzania obrazu: " + e.getMessage());
                    adapter.notifyDataSetChanged();
                });
    }

}
