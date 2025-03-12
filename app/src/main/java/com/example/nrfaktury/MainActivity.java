package com.example.nrfaktury;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
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
    private RecyclerView rvResults;
    private List<String> ocrResults = new ArrayList<>();
    private OCRResultsAdapter adapter;

    private Bitmap originalImage;
    private Bitmap processingImage;

    private int rotationCount = 0;
    private static final int MAX_ROTATIONS = 3;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgPreview = findViewById(R.id.imgPreview);
        rvResults = findViewById(R.id.rvResults);
        MaterialButton btnOpenCamera = findViewById(R.id.btnOpenCamera);
        MaterialButton btnSelectFromGallery = findViewById(R.id.btnSelectFromGallery);
        MaterialButton btnStartOcr = findViewById(R.id.btnStartOcr);


        adapter = new OCRResultsAdapter(this, ocrResults);
        rvResults.setLayoutManager(new LinearLayoutManager(this));
        rvResults.setAdapter(adapter);


        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        if (extras != null) {
                            originalImage = (Bitmap) extras.get("data");
                            imgPreview.setImageBitmap(originalImage);
                            processingImage = originalImage;
                            rotationCount = 0;
                        }
                    }
                });


        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        try {
                            Uri imageUri = result.getData().getData();
                            originalImage = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                            imgPreview.setImageBitmap(originalImage);
                            processingImage = originalImage;
                            rotationCount = 0;
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
            if (processingImage != null) {
                processImageWithMlKit(processingImage);
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
                    recognizedText = recognizedText.replaceAll("WYSTNSENIA", "WYSTAWIENIA");
                    ocrResults.clear();


                    Pattern invoicePattern = Pattern.compile("[^\\s/]+(?:/[^\\s/]+){2,}");
                    Matcher invoiceMatcher = invoicePattern.matcher(recognizedText);
                    if (invoiceMatcher.find()) {
                        String invoiceNumber = invoiceMatcher.group();
                        ocrResults.add("Numer faktury: " + invoiceNumber);
                    } else {
                        ocrResults.add("Nie znaleziono numeru faktury.");
                    }

                    String selectedDate = null;
                    boolean labelFound = false;


                    Pattern exactLabelPattern = Pattern.compile("DATA WYSTAWIENIA", Pattern.CASE_INSENSITIVE);
                    Matcher exactLabelMatcher = exactLabelPattern.matcher(recognizedText);
                    if (exactLabelMatcher.find()) {
                        labelFound = true;
                        int labelEnd = exactLabelMatcher.end();
                        int endIndex = Math.min(recognizedText.length(), labelEnd + 50);
                        String afterLabel = recognizedText.substring(labelEnd, endIndex);
                        Pattern datePattern = Pattern.compile("\\b(\\d{4}[-.]\\d{2}[-.]\\d{2}|\\d{2}[-.]\\d{2}[-.]\\d{4})\\b");
                        Matcher dateAfterLabelMatcher = datePattern.matcher(afterLabel);
                        if (dateAfterLabelMatcher.find()) {
                            selectedDate = dateAfterLabelMatcher.group();
                        }
                    }


                    if (labelFound && selectedDate == null && rotationCount < MAX_ROTATIONS) {
                        rotationCount++;
                        processingImage = rotateBitmap(bitmap, 90);
                        processImageWithMlKit(processingImage);
                        return;
                    }


                    if (selectedDate == null) {
                        Pattern datePattern = Pattern.compile("\\b(\\d{4}[-.]\\d{2}[-.]\\d{2}|\\d{2}[-.]\\d{2}[-.]\\d{4})\\b");
                        Matcher dateMatcher = datePattern.matcher(recognizedText);
                        List<Integer> datePositions = new ArrayList<>();
                        List<String> foundDates = new ArrayList<>();
                        while (dateMatcher.find()) {
                            datePositions.add(dateMatcher.start());
                            foundDates.add(dateMatcher.group());
                        }

                        Pattern wordPattern = Pattern.compile("\\S+");
                        Matcher wordMatcher = wordPattern.matcher(recognizedText);
                        List<Integer> labelPositions = new ArrayList<>();
                        String[] targetLabels = {"WYSTAWIENIA", "WYSTAWIONO", "DNIA"};
                        while (wordMatcher.find()) {
                            String word = wordMatcher.group();
                            for (String target : targetLabels) {
                                if (isSimilar(word, target)) {
                                    labelPositions.add(wordMatcher.start());
                                    break;
                                }
                            }
                        }
                        int minDistance = Integer.MAX_VALUE;
                        for (int labelPos : labelPositions) {
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

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(dp[i - 1][j] + 1,
                        Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost));
            }
        }
        return dp[s1.length()][s2.length()];
    }

    private boolean isSimilar(String s1, String s2) {
        int distance = levenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        double normalized = (double) distance / maxLen;
        return normalized < 0.3;
    }

    private Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }


    private static class OCRResultsAdapter extends RecyclerView.Adapter<OCRResultsAdapter.ViewHolder> {
        private final Context context;
        private final List<String> results;

        OCRResultsAdapter(Context context, List<String> results) {
            this.context = context;
            this.results = results;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            String text = results.get(position);
            ((android.widget.TextView) holder.itemView).setText(text);

            holder.itemView.setOnLongClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Skopiowany tekst", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Skopiowano: " + text, Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(android.view.View itemView) {
                super(itemView);
            }
        }
    }
}
