# Install CNG Italy on Another Android Phone

## 1. Check the offline map exists

```powershell
Get-Item .\build\offline\italy.pmtiles
```

Expected size: about **2.5 GiB**.

## 2. Enable USB debugging on the phone

Enable **Developer options** and **USB debugging**, connect the phone by USB, and accept the debugging prompt.

In PowerShell:

```powershell
$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"
adb devices
```

Note the phone serial if more than one device is connected.

## 3. Install the app

### Only this phone connected

```powershell
.\gradlew.bat installDebug
```

### Multiple phones connected

```powershell
.\gradlew.bat assembleDebug
adb -s HER_SERIAL install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Replace `HER_SERIAL` with the value from `adb devices`.

## 4. Open the app once while online

Open **CNG Italy** and wait until stations/prices appear so the local station database is populated.

## 5. Install the full Italy offline map

### Only this phone connected

```powershell
.\tools\offline-map\install-italy-map.ps1
```

### Multiple phones connected

```powershell
.\tools\offline-map\install-italy-map.ps1 -Serial HER_SERIAL
```

The 2.5 GB transfer can take a while.

## 6. Verify the map

```powershell
adb -s HER_SERIAL shell run-as com.zangrcar.cngitaly ls -lh files/maps/italy.pmtiles
```

Expected size: about **2.5G**.

If only one phone is connected, omit `-s HER_SERIAL`.

## 7. Final offline test

Turn on **airplane mode**, close/reopen the app, and verify:

- offline Italy map loads
- roads/place names appear
- stations/prices/clusters appear
- station details work
- GPS works if Android Location is enabled

## Future app updates

Do **not uninstall** the app, because uninstalling deletes the offline map and local database.

Update with:

```powershell
adb -s HER_SERIAL install -r .\app\build\outputs\apk\debug\app-debug.apk
```

The same `italy.pmtiles` file can be installed on multiple Android phones.
