[![Review Assignment Due Date](https://classroom.github.com/assets/deadline-readme-button-22041afd0340ce965d47ae6ef1cefeee28c7c493a6346c4f15d667ab976d596c.svg)](https://classroom.github.com/a/4-04QCSZ)

# WiFinder
WiFinder allows the user to find their location at the University of Auckland. Currently the application supports a small area on floors G, 1 and 2 in building 302. The images below show the area where data points where collected, so you can expect the application to work in these areas.

**Floor G (0)**

![Floor 0 Data](https://github.com/uoa-compsci399-2025-s1/capstone-project-2025-s1-team-7/blob/main/WiFi-Scan-Data-Images/Floor-0.png)

**Floor 1**

![Floor 1 Data](https://github.com/uoa-compsci399-2025-s1/capstone-project-2025-s1-team-7/blob/main/WiFi-Scan-Data-Images/Floor-1.png)

**Floor 2**

![Floor 2 Data](https://github.com/uoa-compsci399-2025-s1/capstone-project-2025-s1-team-7/blob/main/WiFi-Scan-Data-Images/Floor-2.png)

## Technologies used to create the application
The WiFinder application was made using Android Studio. The languages used were Kotlin and Java.

The project requires an Android Gradle Version of 8.9.0 or above. As long as you install the latest version of Android Studio, there should be no problems running this project.

**Extra tools can be found under other branches:**
- The interpolation branch has scripts available to create interpolated data points based on the collected data points
- The position-tool branch has a local webpage program that allows the user to select points on the map to get an X and Y coordinate of the floor image
- The model-testing branch has a build of the Android application that allows developers to test different machine learning models
- The mainWithPF branch has a build of the Android application which implements the Particle Filter. As of writing this README, the Particle Filter still has some issues which is why it's under a separate branch.

## Builds of the Application
You can find development builds [here](https://github.com/uoa-compsci399-2025-s1/capstone-project-2025-s1-team-7/releases/tag/COMPSCI-399-Final) (Android only)

## How to setup the application
- Install the latest version of Android Studio
- Clone the Repository
- Open Android Studio and go through the setup process
- Once you get to the project selection page, select and open the cloned repository

- Go to the project view on the left side, open Gradle Scripts dropdown and open the build.gradle.kts file
- A prompt will show up at the top of the editor asking you to sync the gradle files. Click the "Sync Now" option.

- Once the gradle files are synced, you can build and run the application on an emulator or load it onto an Android phone.

For more information on running the application on your emulator or phone, refer to the [Android Studio Developer Page](https://developer.android.com/studio/run/device)

## Note on running the application
The main branch currently has an update rate of 1 second. However this update rate can only be achieved if you disable WiFi Scan Throttling in the developer options of your Android phone. If you don't disable this, the app will update at most, 4 times every 2 minutes. [Learn more about WiFi Scan Throttling](https://developer.android.com/develop/connectivity/wifi/wifi-scan#wifi-scan-throttling)

There is a variable under [MapViewModel.kt](https://github.com/uoa-compsci399-2025-s1/capstone-project-2025-s1-team-7/blob/main/app/src/main/java/com/example/compsci399testproject/viewmodel/MapViewModel.kt) called _wifiScanRate where you can change this update rate.

## Future Work For the Project (not specifically relevant for CS742 students)
- Client Server Architecture for better machine learning models, also allows the system to update the models as the application is being used
- Better data collection methods, such as autonomous robotic scanning, or scan tool improvements
- Integration with the official UoA Maps API, allowing for dynamic loading of floor images, rooms etc.
