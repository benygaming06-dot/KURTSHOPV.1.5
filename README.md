# KURTSHOP INVENTORY V1.5

Android inventory app for KURT DHYLAN MOTO SHOP.

## V1.5 cloud architecture
- Firebase Authentication (email/password API) is used for the actual login identity.
- Firestore stores the shared shop state: products, services, sales history, stock history and account roles.
- Passwords are NOT stored in Firestore.
- Each phone keeps an offline local cache so the app can still display/use its last known data without internet.
- On cloud login, the phone downloads the current shared shop state.
- Inventory/sales/account changes are pushed to Firestore when online.

## Firebase setup (required for multi-phone sync)
1. Create a Firebase project in the Firebase Console.
2. Enable **Authentication > Sign-in method > Email/Password**.
3. Create a **Cloud Firestore** database.
4. In Project settings, copy the Firebase **Web API Key** and **Project ID**.
5. Open:
   `app/src/main/java/com/kurtmotoshop/v1/BackendConfig.java`
6. Replace:
   `YOUR_FIREBASE_WEB_API_KEY`
   and
   `YOUR_FIREBASE_PROJECT_ID`
   with your project values.
7. Add Firestore rules appropriate for your shop. For the first V1.5 setup, use this authenticated-only rule so the bootstrap can create the shop and member records:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /shops/{shopId} {
      allow read, write: if request.auth != null;
    }
    match /shops/{shopId}/members/{uid} {
      allow read, write: if request.auth != null;
    }
  }
}
```

After the first private deployment is working, tighten the rules to membership/role checks before public distribution.

For a production deployment, tighten these rules around shop membership/roles before distributing the APK publicly.

## First cloud login
- Configure Firebase, build/install V1.5, then log in as `owner / owner123`.
- If the shop document does not exist yet, V1.5 initializes it and seeds the V1.4 assistant and mechanic identities:
  - assistant / assistant123
  - mechanic / mechanic123
- After initialization, all phones using the same Firebase project and SHOP_ID share the Firestore shop state.

## Important
`BackendConfig.java` contains project configuration that is specific to the shop. Do not commit a private server credential or Firebase Admin SDK key into the Android app. The Web API key is intended for client use; Firestore Security Rules are what protect the database.

## Build
The GitHub Actions workflow installs Java 17, Android SDK 35 and Gradle 8.9, then builds:

`gradle :app:assembleDebug`

APK output:
`app/build/outputs/apk/debug/app-debug.apk`
