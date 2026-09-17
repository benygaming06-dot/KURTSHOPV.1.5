package com.kurtmotoshop.v1;

/** Firebase configuration for KURTSHOP INVENTORY V1.5. */
public final class BackendConfig {
    private BackendConfig() {}

    // Replace these two values with your Firebase project values.
    public static final String FIREBASE_API_KEY = "YOUR_FIREBASE_WEB_API_KEY";
    public static final String FIREBASE_PROJECT_ID = "YOUR_FIREBASE_PROJECT_ID";

    // This is the shared Firestore document used by all phones in this shop.
    public static final String SHOP_ID = "kurt-dhylan-moto-shop";
}
