/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.andlib.utility;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import com.todoroo.andlib.service.ExceptionService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map.Entry;

/**
 * Android Utility Classes
 *
 * @author Tim Su <tim@todoroo.com>
 *
 */
public class AndroidUtilities {

    public static final String SEPARATOR_ESCAPE = "!PIPE!"; //$NON-NLS-1$
    public static final String SERIALIZATION_SEPARATOR = "|"; //$NON-NLS-1$

    // --- utility methods

    /** Suppress virtual keyboard until user's first tap */
    public static void suppressVirtualKeyboard(final TextView editor) {
        final int inputType = editor.getInputType();
        editor.setInputType(InputType.TYPE_NULL);
        editor.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                editor.setInputType(inputType);
                editor.setOnTouchListener(null);
                return false;
            }
        });
    }

    /** Read a bitmap from the specified file, scaling if necessary
     *  Returns null if scaling failed after several tries */
    private static final int[] SAMPLE_SIZES = { 1, 2, 4, 6, 8, 10 };
    private static final int MAX_DIM = 1024;
    public static Bitmap readScaledBitmap(String file) {
        Bitmap bitmap = null;
        int tries = 0;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        while((bitmap == null || (bitmap.getWidth() > MAX_DIM || bitmap.getHeight() > MAX_DIM)) && tries < SAMPLE_SIZES.length) {
            opts.inSampleSize = SAMPLE_SIZES[tries];
            try {
                bitmap = BitmapFactory.decodeFile(file, opts);
            } catch (OutOfMemoryError e) {
                // Too big
                Log.e("decode-bitmap", "Out of memory with sample size " + opts.inSampleSize, e);  //$NON-NLS-1$//$NON-NLS-2$
            }
            tries++;
        }

        return bitmap;
    }

    /**
     * Start the given intent, handling security exceptions if they arise
     * @param request request code. if negative, no request.
     */
    public static void startExternalIntent(Context context, Intent intent, int request) {
        try {
            if(request > -1 && context instanceof Activity) {
                ((Activity) context).startActivityForResult(intent, request);
            } else {
                context.startActivity(intent);
            }
        } catch (Exception e) {
            getExceptionService().displayAndReportError(context,
                    "start-external-intent-" + intent.toString(), //$NON-NLS-1$
                    e);
        }
    }

    /**
     * Put an arbitrary object into a {@link ContentValues}
     */
    public static void putInto(ContentValues target, String key, Object value, boolean errorOnFail) {
        if (value instanceof Boolean) {
            target.put(key, (Boolean) value);
        } else if (value instanceof Byte) {
            target.put(key, (Byte) value);
        } else if (value instanceof Double) {
            target.put(key, (Double) value);
        } else if (value instanceof Float) {
            target.put(key, (Float) value);
        } else if (value instanceof Integer) {
            target.put(key, (Integer) value);
        } else if (value instanceof Long) {
            target.put(key, (Long) value);
        } else if (value instanceof Short) {
            target.put(key, (Short) value);
        } else if (value instanceof String) {
            target.put(key, (String) value);
        } else if (errorOnFail) {
            throw new UnsupportedOperationException("Could not handle type " + //$NON-NLS-1$
                    value.getClass());
        }
    }

    /**
     * Put an arbitrary object into a {@link ContentValues}
     */
    public static void putInto(Bundle target, String key, Object value, boolean errorOnFail) {
        if (value instanceof Boolean) {
            target.putBoolean(key, (Boolean) value);
        } else if (value instanceof Byte) {
            target.putByte(key, (Byte) value);
        } else if (value instanceof Double) {
            target.putDouble(key, (Double) value);
        } else if (value instanceof Float) {
            target.putFloat(key, (Float) value);
        } else if (value instanceof Integer) {
            target.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            target.putLong(key, (Long) value);
        } else if (value instanceof Short) {
            target.putShort(key, (Short) value);
        } else if (value instanceof String) {
            target.putString(key, (String) value);
        } else if (errorOnFail) {
            throw new UnsupportedOperationException("Could not handle type " + //$NON-NLS-1$
                    value.getClass());
        }
    }

    // --- serialization

    /**
     * Return index of value in array
     * @param array array to search
     * @param value value to look for
     */
    public static <TYPE> int indexOf(TYPE[] array, TYPE value) {
        for(int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Return index of value in integer array
     * @param array array to search
     * @param value value to look for
     */
    public static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Serializes a content value into a string
     */
    public static String contentValuesToSerializedString(ContentValues source) {
        StringBuilder result = new StringBuilder();
        for(Entry<String, Object> entry : source.valueSet()) {
            addSerialized(result, entry.getKey(), entry.getValue());
        }
        return result.toString();
    }

    /** add serialized helper */
    private static void addSerialized(StringBuilder result,
            String key, Object value) {
        result.append(key.replace(SERIALIZATION_SEPARATOR, SEPARATOR_ESCAPE)).append(
                SERIALIZATION_SEPARATOR);
        if(value instanceof Integer) {
            result.append('i').append(value);
        } else if(value instanceof Double) {
            result.append('d').append(value);
        } else if(value instanceof Long) {
            result.append('l').append(value);
        } else if(value instanceof String) {
            result.append('s').append(value.toString().replace(SERIALIZATION_SEPARATOR, SEPARATOR_ESCAPE));
        } else if (value instanceof Boolean) {
            result.append('b').append(value);
        } else {
            throw new UnsupportedOperationException(value.getClass().toString());
        }
        result.append(SERIALIZATION_SEPARATOR);
    }

    /**
     * Serializes a {@link android.os.Bundle} into a string
     */
    public static String bundleToSerializedString(Bundle source) {
        StringBuilder result = new StringBuilder();
        if (source == null) {
            return null;
        }

        for(String key : source.keySet()) {
            addSerialized(result, key, source.get(key));
        }
        return result.toString();
    }

    /**
     * Turn ContentValues into a string
     */
    public static ContentValues contentValuesFromSerializedString(String string) {
        if(string == null) {
            return new ContentValues();
        }

        ContentValues result = new ContentValues();
        fromSerialized(string, result, new SerializedPut<ContentValues>() {
            @Override
            public void put(ContentValues object, String key, char type, String value) throws NumberFormatException {
                switch(type) {
                case 'i':
                    object.put(key, Integer.parseInt(value));
                    break;
                case 'd':
                    object.put(key, Double.parseDouble(value));
                    break;
                case 'l':
                    object.put(key, Long.parseLong(value));
                    break;
                case 's':
                    object.put(key, value.replace(SEPARATOR_ESCAPE, SERIALIZATION_SEPARATOR));
                    break;
                case 'b':
                    object.put(key, Boolean.parseBoolean(value));
                    break;
                }
            }
        });
        return result;
    }

    /**
     * Turn {@link android.os.Bundle} into a string
     */
    public static Bundle bundleFromSerializedString(String string) {
        if(string == null) {
            return new Bundle();
        }

        Bundle result = new Bundle();
        fromSerialized(string, result, new SerializedPut<Bundle>() {
            @Override
            public void put(Bundle object, String key, char type, String value) throws NumberFormatException {
                switch(type) {
                case 'i':
                    object.putInt(key, Integer.parseInt(value));
                    break;
                case 'd':
                    object.putDouble(key, Double.parseDouble(value));
                    break;
                case 'l':
                    object.putLong(key, Long.parseLong(value));
                    break;
                case 's':
                    object.putString(key, value.replace(SEPARATOR_ESCAPE, SERIALIZATION_SEPARATOR));
                    break;
                case 'b':
                    object.putBoolean(key, Boolean.parseBoolean(value));
                    break;
                }
            }
        });
        return result;
    }

    public interface SerializedPut<T> {
        public void put(T object, String key, char type, String value) throws NumberFormatException;
    }

    private static <T> void fromSerialized(String string, T object, SerializedPut<T> putter) {
        String[] pairs = string.split("\\" + SERIALIZATION_SEPARATOR); //$NON-NLS-1$
        for(int i = 0; i < pairs.length; i += 2) {
            try {
                String key = pairs[i].replaceAll(SEPARATOR_ESCAPE, SERIALIZATION_SEPARATOR);
                String value = pairs[i+1].substring(1);
                try {
                    putter.put(object, key, pairs[i+1].charAt(0), value);
                } catch (NumberFormatException e) {
                    // failed parse to number
                    putter.put(object, key, 's', value);
                }
            } catch (IndexOutOfBoundsException e) {
                Log.e("deserialize", "Badly formed serialization: " + string, e);
            }
        }
    }




    /**
     * Turn ContentValues into a string
     */
    public static ContentValues contentValuesFromString(String string) {
        if(string == null) {
            return null;
        }

        String[] pairs = string.split("=");
        ContentValues result = new ContentValues();
        String key = null;
        for(int i = 0; i < pairs.length; i++) {
            String newKey = null;
            int lastSpace = pairs[i].lastIndexOf(' ');
            if(lastSpace != -1) {
                newKey = pairs[i].substring(lastSpace + 1);
                pairs[i] = pairs[i].substring(0, lastSpace);
            } else {
                newKey =  pairs[i];
            }
            if(key != null) {
                result.put(key.trim(), pairs[i].trim());
            }
            key = newKey;
        }
        return result;
    }

    /**
     * Returns true if a and b or null or a.equals(b)
     */
    public static boolean equals(Object a, Object b) {
        if(a == null && b == null) {
            return true;
        }
        if(a == null) {
            return false;
        }
        return a.equals(b);
    }

    /**
     * Copy a file from one place to another
     * @throws Exception
     */
    public static void copyFile(File in, File out) throws Exception {
        FileInputStream fis = new FileInputStream(in);
        FileOutputStream fos = new FileOutputStream(out);
        try {
            copyStream(fis, fos);
        } catch (Exception e) {
            throw e;
        } finally {
            fis.close();
            fos.close();
        }
    }

    /**
     * Copy stream from source to destination
     * @throws IOException
     */
    public static void copyStream(InputStream source, OutputStream dest) throws IOException {
        int bytes;
        byte[] buffer;
        int BUFFER_SIZE = 1024;
        buffer = new byte[BUFFER_SIZE];
        while ((bytes = source.read(buffer)) != -1) {
            if (bytes == 0) {
                bytes = source.read();
                if (bytes < 0) {
                    break;
                }
                dest.write(bytes);
                dest.flush();
                continue;
            }

            dest.write(buffer, 0, bytes);
            dest.flush();
        }
    }

    /**
     * @return Android SDK version as an integer. Works on all versions
     */
    public static int getSdkVersion() {
        return Integer.parseInt(android.os.Build.VERSION.SDK);
    }

    /**
     * Sort files by date so the newest file is on top
     */
    public static void sortFilesByDateDesc(File[] files) {
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return Long.valueOf(o2.lastModified()).compareTo(Long.valueOf(o1.lastModified()));
            }
        });
    }

    /**
     * Sleep, ignoring interruption. Before using this method, think carefully
     * about why you are ignoring interruptions.
     */
    public static void sleepDeep(long l) {
        try {
            Thread.sleep(l);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    /**
     * If you want to set a transition, please use this method rather than <code>callApiMethod</code> to ensure
     * you really pass an Activity-instance.
     *
     * @param activity the activity-instance for which to set the finish-transition
     * @param enterAnim the incoming-transition of the next activity
     * @param exitAnim the outgoing-transition of this activity
     */
    public static void callOverridePendingTransition(Activity activity, int enterAnim, int exitAnim) {
        callApiMethod(5,
                activity,
                "overridePendingTransition", //$NON-NLS-1$
                new Class<?>[] { Integer.TYPE, Integer.TYPE },
                enterAnim, exitAnim);
    }

    /**
     * Call a method via reflection if API level is at least minSdk
     * @param minSdk minimum sdk number (i.e. 8)
     * @param receiver object to call method on
     * @param methodName method name to call
     * @param params method parameter types
     * @param args arguments
     */
    public static void callApiMethod(int minSdk, Object receiver,
            String methodName, Class<?>[] params, Object... args) {
        if(getSdkVersion() < minSdk) {
            return;
        }

        AndroidUtilities.callMethod(receiver.getClass(),
                receiver, methodName, params, args);
    }

    /**
     * Call a method via reflection
     * @param receiver object to call method on (can be null)
     * @param methodName method name to call
     * @param params method parameter types
     * @param args arguments
     * @return method return value, or null if nothing was called or exception
     */
    public static void callMethod(Class<?> cls, Object receiver,
            String methodName, Class<?>[] params, Object... args) {
        try {
            Method method = cls.getMethod(methodName, params);
            method.invoke(receiver, args);
        } catch (SecurityException e) {
            getExceptionService().reportError("call-method", e);
        } catch (NoSuchMethodException e) {
            getExceptionService().reportError("call-method", e);
        } catch (IllegalArgumentException e) {
            getExceptionService().reportError("call-method", e);
        } catch (IllegalAccessException e) {
            getExceptionService().reportError("call-method", e);
        } catch (InvocationTargetException e) {
            getExceptionService().reportError("call-method", e);
        }
    }

    /**
     * Join items to a list
     */
    public static <T> T[] addToArray(Class<T> type, T[] list, T... newItems) {
        int originalListLength = 0;
        int length = 0;
        if (list != null) {
            originalListLength = list.length;
            length += list.length;
        }
        if (newItems != null) {
            length += newItems.length;
        }

        T[] newList = (T[]) Array.newInstance(type, length);
        if (list != null) {
            for(int i = 0; i < list.length; i++) {
                newList[i] = list[i];
            }
        }
        if (newItems != null) {
            for(int i = 0; i < newItems.length; i++) {
                newList[originalListLength + i] = newItems[i];
            }
        }
        return newList;
    }

    // --- internal

    private static ExceptionService exceptionService = null;

    private static ExceptionService getExceptionService() {
        if(exceptionService == null) {
            synchronized (AndroidUtilities.class) {
                if (exceptionService == null) {
                    exceptionService = new ExceptionService();
                }
            }
        }
        return exceptionService;
    }

    /**
     * Capitalize the first character
     */
    public static String capitalize(String string) {
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

    /**
     * Dismiss the keyboard if it is displayed by any of the listed views
     * @param views - a list of views that might potentially be displaying the keyboard
     */
    public static void hideSoftInputForViews(Context context, View...views) {
        InputMethodManager imm = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        for (View v : views) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    /**
     * Returns true if the screen is large or xtra large
     */
    public static boolean isTabletSized(Context context) {
        if (context.getPackageManager().hasSystemFeature("com.google.android.tv")) //$NON-NLS-1$
        {
            return true;
        }
        int size = context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK;

        if (size == Configuration.SCREENLAYOUT_SIZE_XLARGE) {
            return true;
        } else if (size == Configuration.SCREENLAYOUT_SIZE_LARGE) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            float width = metrics.widthPixels / metrics.density;
            float height = metrics.heightPixels / metrics.density;

            float effectiveWidth = Math.min(width, height);
            float effectiveHeight = Math.max(width, height);

            return (effectiveWidth >= MIN_TABLET_WIDTH && effectiveHeight >= MIN_TABLET_HEIGHT);
        } else {
            return false;
        }
    }

    public static final int MIN_TABLET_WIDTH = 550;
    public static final int MIN_TABLET_HEIGHT = 800;

    /**
     * Wraps a call to Activity.unregisterReceiver in a try/catch block to prevent
     * exceptions being thrown if receiver was never registered with that activity
     */
    public static void tryUnregisterReceiver(Activity activity, BroadcastReceiver receiver) {
        try {
            activity.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            // Receiver wasn't registered for some reason
        }
    }

    /**
     * Returns the final word characters after the last '.'
     */
    public static String getFileExtension(String file) {
        int index = file.lastIndexOf('.');
        String extension = "";
        if (index > 0) {
            extension = file.substring(index + 1);
            if (!extension.matches("\\w+")) {
                extension = "";
            }
        }
        return extension;
    }
}
