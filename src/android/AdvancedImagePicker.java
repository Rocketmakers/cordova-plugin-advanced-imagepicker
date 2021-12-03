package de.einfachhans.AdvancedImagePicker;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import android.os.Build;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import gun0912.tedimagepicker.builder.TedImagePicker;

public class AdvancedImagePicker extends CordovaPlugin {

    private CallbackContext _callbackContext;

    private String _thumbPrefix = "advanced_image_picker_";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this._callbackContext = callbackContext;

        try {
            if (action.equals("present")) {
                this.presentFullScreen(args);
                return true;
            } else {
                returnError(AdvancedImagePickerErrorCodes.UnsupportedAction);
                return false;
            }
        } catch (JSONException exception) {
            returnError(AdvancedImagePickerErrorCodes.WrongJsonObject);
        } catch (Exception exception) {
            returnError(AdvancedImagePickerErrorCodes.UnknownError, exception.getMessage());
        }

        return true;
    }

    private void presentFullScreen(JSONArray args) throws JSONException {
        JSONObject options = args.getJSONObject(0);
        String mediaType = options.optString("mediaType", "IMAGE");
        boolean showCameraTile = options.optBoolean("showCameraTile", true);
        String scrollIndicatorDateFormat = options.optString("scrollIndicatorDateFormat");
        boolean showTitle = options.optBoolean("showTitle", true);
        String title = options.optString("title");
        boolean zoomIndicator = options.optBoolean("zoomIndicator", true);
        int min = options.optInt("min");
        String defaultMinCountMessage = "You need to select a minimum of " + (min == 1 ? "one picture" : min + " pictures");
        String minCountMessage = options.optString("minCountMessage", defaultMinCountMessage);
        int max = options.optInt("max");
        String defaultMaxCountMessage = "You can select a maximum of " + max + " pictures";
        String maxCountMessage = options.optString("maxCountMessage", defaultMaxCountMessage);
        String buttonText = options.optString("buttonText");
        boolean asDropdown = options.optBoolean("asDropdown");
        boolean asBase64 = options.optBoolean("asBase64");
        boolean asJpeg = options.optBoolean("asJpeg");

        if (min < 0 || max < 0) {
            this.returnError(AdvancedImagePickerErrorCodes.WrongJsonObject, "Min and Max can not be less then zero.");
            return;
        }

        if (max != 0 && max < min) {
            this.returnError(AdvancedImagePickerErrorCodes.WrongJsonObject, "Max can not be smaller than Min.");
            return;
        }

        TedImagePicker.Builder builder = TedImagePicker.with(this.cordova.getContext())
                .showCameraTile(showCameraTile)
                .showTitle(showTitle)
                .zoomIndicator(zoomIndicator)
                .errorListener(error -> {
                    this.returnError(AdvancedImagePickerErrorCodes.UnknownError, error.getMessage());
                });

        if (!scrollIndicatorDateFormat.equals("")) {
            builder.scrollIndicatorDateFormat(scrollIndicatorDateFormat);
        }
        if (!title.equals("")) {
            builder.title(title);
        }
        if (!buttonText.equals("")) {
            builder.buttonText(buttonText);
        }
        if (asDropdown) {
            builder.dropDownAlbum();
        }
        String type = "image";
        if (mediaType.equals("VIDEO")) {
            builder.video();
            type = "video";
        }

        if (max == 1) {
            String finalType = type;
            builder.start(result -> {
                this.handleResult(result, asBase64, finalType, asJpeg);
            });
        } else {
            if (min > 0) {
                builder.min(min, minCountMessage);
            }
            if (max > 0) {
                builder.max(max, maxCountMessage);
            }

            String finalType1 = type;
            builder.startMultiImage(result -> {
                this.handleResult(result, asBase64, finalType1, asJpeg);
            });
        }
    }

    private void handleResult(Uri uri, boolean asBase64, String type, boolean asJpeg) {
        List<Uri> list = new ArrayList<>();
        list.add(uri);
        this.handleResult(list, asBase64, type, asJpeg);
    }

    private void handleResult(List<? extends Uri> uris, boolean asBase64, String type, boolean asJpeg) {
        JSONArray result = new JSONArray();
        for (Uri uri : uris) {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("type", type);
            resultMap.put("isBase64", asBase64);
            if (asBase64) {
                try {
                    resultMap.put("src", type.equals("video") ? this.encodeVideo(uri) : this.encodeImage(uri, asJpeg));
                } catch (Exception e) {
                    e.printStackTrace();
                    this.returnError(AdvancedImagePickerErrorCodes.UnknownError, e.getMessage());
                    return;
                }
            } else {
                resultMap.put("src", uri.toString());
            }
            // Deal with thumbs
            final String thumbUri = this.getThumbPath(uri);
            resultMap.put("thumb", thumbUri);
            result.put(new JSONObject(resultMap));
        }
        this._callbackContext.success(result);
    }

    private String encodeVideo(Uri uri) throws IOException {
        final InputStream videoStream = this.cordova.getContext().getContentResolver().openInputStream(uri);
        byte[] bytes;
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((bytesRead = videoStream.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        bytes = output.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    private String encodeImage(Uri uri, boolean asJpeg) throws FileNotFoundException {
        final InputStream imageStream = this.cordova.getContext().getContentResolver().openInputStream(uri);
        final Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
        return encodeImage(selectedImage, asJpeg);
    }

    private String getThumbPath(Uri uri) {
        try {
            // Get thumbnail image
            final Bitmap bitmap;
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
                bitmap = this.cordova.getContext().getContentResolver().loadThumbnail(uri, new Size(100, 100), null);
            } else {
                bitmap = MediaStore.Images.Thumbnails.getThumbnail(this.cordova.getContext().getContentResolver(), Long.parseLong(uri.getLastPathSegment()), MediaStore.Images.Thumbnails.MINI_KIND, null);
            }

            // Compress image
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);

            // Create file
            final File outputFile = this.createNewTempImage();

            // Write file to disk
            try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                baos.writeTo(outputStream);
            }

            // Return URI
            return outputFile.toURI().toString();
        }
        catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private File createNewTempImage() throws IOException {
        final String id = UUID.randomUUID().toString();
        final String fileName = this._thumbPrefix + id;
        return File.createTempFile(fileName, ".jpg", this.cordova.getContext().getCacheDir());
    }

    private String encodeImage(Bitmap bm, boolean asJpeg) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (asJpeg) {
            bm.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        } else {
            bm.compress(Bitmap.CompressFormat.PNG, 80, baos);
        }
        byte[] b = baos.toByteArray();
        return Base64.encodeToString(b, Base64.DEFAULT);
    }

    private void returnError(AdvancedImagePickerErrorCodes errorCode) {
        returnError(errorCode, null);
    }

    private void returnError(AdvancedImagePickerErrorCodes errorCode, String message) {
        if (_callbackContext != null) {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("code", errorCode.value);
            resultMap.put("message", message == null ? "" : message);
            _callbackContext.error(new JSONObject(resultMap));
            _callbackContext = null;
        }
    }
}
