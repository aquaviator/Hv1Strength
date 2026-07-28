cat << 'INNER' > app/src/main/res/drawable/ic_launcher_background.xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#0A0D10"
        android:pathData="M0,0h108v108h-108z"/>
</vector>
INNER

cat << 'INNER' > app/src/main/res/drawable/ic_launcher_foreground.xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="0.825"
        android:scaleY="0.825"
        android:translateX="12.75"
        android:translateY="12.75">
        <!-- Outer Circular Badge Ring -->
        <path
            android:strokeColor="#FFFFFF"
            android:strokeWidth="4"
            android:pathData="M 50 10 A 40 40 0 1 0 50 90 A 40 40 0 1 0 50 10" />
        <!-- Head -->
        <path
            android:fillColor="#FFFFFF"
            android:pathData="M 50 37.5 A 5.5 5.5 0 1 0 50 48.5 A 5.5 5.5 0 1 0 50 37.5" />
        <!-- Torso -->
        <path
            android:strokeColor="#FFFFFF"
            android:strokeWidth="5"
            android:strokeLineCap="round"
            android:pathData="M 50 49 L 50 68" />
        <!-- Legs -->
        <path
            android:strokeColor="#FFFFFF"
            android:strokeWidth="4"
            android:strokeLineCap="round"
            android:pathData="M 50 68 L 41 84 M 50 68 L 59 84" />
        <!-- Arms -->
        <path
            android:strokeColor="#FFFFFF"
            android:strokeWidth="4"
            android:strokeLineCap="round"
            android:pathData="M 50 52 L 32 32 M 50 52 L 68 32" />
        <!-- Barbell Bar -->
        <path
            android:strokeColor="#0066FF"
            android:strokeWidth="4"
            android:strokeLineCap="round"
            android:pathData="M 18 32 L 82 32" />
        <!-- Barbell Weight Plates - Inner -->
        <path
            android:strokeColor="#0066FF"
            android:strokeWidth="5.5"
            android:strokeLineCap="round"
            android:pathData="M 26 20 L 26 44 M 74 20 L 74 44" />
        <!-- Barbell Weight Plates - Outer -->
        <path
            android:strokeColor="#0066FF"
            android:strokeWidth="4"
            android:strokeLineCap="round"
            android:pathData="M 20 24 L 20 40 M 80 24 L 80 40" />
    </group>
</vector>
INNER

cat << 'INNER' > app/src/main/res/drawable/ic_launcher_foreground_monochrome.xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="0.825"
        android:scaleY="0.825"
        android:translateX="12.75"
        android:translateY="12.75">
        <!-- Outer Circular Badge Ring -->
        <path
            android:strokeColor="#000000"
            android:strokeWidth="4"
            android:pathData="M 50 10 A 40 40 0 1 0 50 90 A 40 40 0 1 0 50 10" />
        <!-- Head -->
        <path
            android:fillColor="#000000"
            android:pathData="M 50 37.5 A 5.5 5.5 0 1 0 50 48.5 A 5.5 5.5 0 1 0 50 37.5" />
        <!-- Torso -->
        <path
            android:strokeColor="#000000"
            android:strokeWidth="5"
            android:strokeLineCap="round"
            android:pathData="M 50 49 L 50 68" />
        <!-- Legs -->
        <path
            android:strokeColor="#000000"
            android:strokeWidth="4"
            android:strokeLineCap="round"
            android:pathData="M 50 68 L 41 84 M 50 68 L 59 84" />
        <!-- Arms -->
        <path
            android:strokeColor="#000000"
            android:strokeWidth="4"
            android:strokeLineCap="round"
            android:pathData="M 50 52 L 32 32 M 50 52 L 68 32" />
        <!-- Barbell Bar -->
        <path
            android:strokeColor="#000000"
            android:strokeWidth="4"
            android:strokeLineCap="round"
            android:pathData="M 18 32 L 82 32" />
        <!-- Barbell Weight Plates - Inner -->
        <path
            android:strokeColor="#000000"
            android:strokeWidth="5.5"
            android:strokeLineCap="round"
            android:pathData="M 26 20 L 26 44 M 74 20 L 74 44" />
        <!-- Barbell Weight Plates - Outer -->
        <path
            android:strokeColor="#000000"
            android:strokeWidth="4"
            android:strokeLineCap="round"
            android:pathData="M 20 24 L 20 40 M 80 24 L 80 40" />
    </group>
</vector>
INNER

cp app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/drawable/ic_splash_icon.xml

git add app/src/main/res/drawable/ic_launcher_background.xml app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/drawable/ic_launcher_foreground_monochrome.xml app/src/main/res/drawable/ic_splash_icon.xml
