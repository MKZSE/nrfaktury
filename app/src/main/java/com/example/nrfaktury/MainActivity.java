package com.example.nrfaktury;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
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
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
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

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgPreview = findViewById(R.id.imgPreview);
        lstResults = findViewById(R.id.lstResults);
        Button btnOpenCamera = findViewById(R.id.btnOpenCamera);
        Button btnSelectFromGallery = findViewById(R.id.btnSelectFromGallery);
        Button btnStartOcr = findViewById(R.id.btnStartOcr);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ocrResults);
        lstResults.setAdapter(adapter);

        cameraLauncher = registerForActivityResult(
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

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        try {
                            Uri imageUri = result.getData().getData();
                            capturedImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                            imgPreview.setImageBitmap(capturedImage);
                        } catch (IOException e) {
                            ocrResults.add("Błąd wczytywania obrazu: " + e.getMessage());
                            adapter.notifyDataSetChanged();
                        }
                    }
                });

        btnOpenCamera.setOnClickListener(view -> {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(cameraIntent);
        });

        btnSelectFromGallery.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        btnStartOcr.setOnClickListener(view -> {
            if (capturedImage != null) {
                processImageWithMlKit(capturedImage);
            } else {
                ocrResults.add("Najpierw wykonaj zdjęcie lub wybierz obraz!");
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
                    ocrResults.clear();


                    Pattern invoicePattern = Pattern.compile("[^\\s/]+(?:/[^\\s/]+){2,}");
                    Matcher invoiceMatcher = invoicePattern.matcher(recognizedText);

                    if (invoiceMatcher.find()) {
                        String invoiceNumber = invoiceMatcher.group();
                        ocrResults.add("Numer faktury: " + invoiceNumber);
                    } else {
                        ocrResults.add("Nie znaleziono numeru faktury.");
                    }


                    Pattern datePattern = Pattern.compile("\\b(\\d{4}[-.]\\d{2}[-.]\\d{2}|\\d{2}[-.]\\d{2}[-.]\\d{4})\\b");
                    Matcher dateMatcher = datePattern.matcher(recognizedText);


                    Pattern labelPattern = Pattern.compile("(DATA WYSTAWIENIA|WYSTAWIONO|DNIA)");


                    List<Integer> datePositions = new ArrayList<>();
                    List<String> foundDates = new ArrayList<>();

                    while (dateMatcher.find()) {
                        datePositions.add(dateMatcher.start());
                        foundDates.add(dateMatcher.group());
                    }


                    Matcher labelMatcher = labelPattern.matcher(recognizedText);
                    String selectedDate = null;
                    int minDistance = Integer.MAX_VALUE;

                    while (labelMatcher.find()) {
                        int labelPos = labelMatcher.start();


                        for (int i = 0; i < datePositions.size(); i++) {
                            int distance = Math.abs(labelPos - datePositions.get(i));
                            if (distance < minDistance) {
                                minDistance = distance;
                                selectedDate = foundDates.get(i);
                            }
                        }
                    }


                    if (selectedDate == null && !foundDates.isEmpty()) {
                        selectedDate = foundDates.get(0);
                    }

                    
                    if (selectedDate != null) {
                        ocrResults.add("Data wystawienia: " + selectedDate);
                    } else {
                        ocrResults.add("Nie znaleziono daty wystawienia.");
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
