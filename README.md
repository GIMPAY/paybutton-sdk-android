# GIM PayButton SDK

<p align="center">
  <a href="https://paysky.io/" target="_blank">
    <img width="440" src="https://i.ibb.co/0FTNnDb/83ac8f45c38ae9af0b22cfaace84c18362b95a8e7db0622f196a78b85f23ecec.png">
  </a>
</p>

The **PayButton SDK** simplifies integrating card and wallet payments into your Android application.  
It provides a ready-made UI that guides users through the payment process and displays a summary screen after completion.

---

## 📌 Getting Started

Follow these steps to set up the SDK in your project.

---

## ⚙️ Prerequisites

- JDK **1.7+**
- Android Studio installed
- Android project with **minSdkVersion 17+**
- AndroidX enabled

---

## 📦 Installation

1. Open your Android project.

2. In **project-level `build.gradle`**, add JitPack:

```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

3. In **app-level `build.gradle`**, add:

```gradle
dependencies {
    implementation 'com.github.GIMPAY:paybutton:1.0.0'
}
```

4. Sync your project.

> ⚠️ Note: Check GitHub releases for the latest version.

---

## 🚀 Deployment

Before going live, obtain the following from PaySky:

- Merchant ID
- Terminal ID
- Secure Hash Key

🔐 **Security Tip:**  
Always encrypt and securely store sensitive credentials.

---

## 🛠️ Usage

### 1. Initialize PayButton

```java
PayButton payButton = new PayButton(context);
```

---

### 2. Required Parameters

You must provide:

- Merchant ID
- Terminal ID
- Payment Amount
- Currency Code ([ISO Codes](https://www.iban.com/currency-codes))
- Secure Hash Key
- Transaction Reference (optional)

---

### 3. Customer Types

#### 🔹 Not Subscribed (Mobile)

```java
payButton.setMerchantId(merchantId);
payButton.setTerminalId(terminalId);
payButton.setPayAmount(amount);
payButton.setCurrencyCode(currencyCode);
payButton.setMerchantSecureHash("secure_hash_key");
payButton.setProductionStatus(PRODUCTION);
payButton.setTransactionReferenceNumber(AppUtils.generateRandomNumber());
        payButton.setCustomerMobile("xxxxxxxxxx");
```

---

#### 🔹 Not Subscribed (Email)

```java
payButton.setMerchantId(merchantId);
payButton.setTerminalId(terminalId);
payButton.setPayAmount(amount);
payButton.setCurrencyCode(currencyCode);
payButton.setMerchantSecureHash("secure_hash_key");
payButton.setProductionStatus(PRODUCTION);
payButton.setTransactionReferenceNumber(AppUtils.generateRandomNumber());
        payButton.setCustomerEmail("joe@name.com");
```

---

#### 🔹 Subscribed User

```java
payButton.setMerchantId(merchantId);
payButton.setTerminalId(terminalId);
payButton.setPayAmount(amount);
payButton.setCurrencyCode(currencyCode);
payButton.setMerchantSecureHash("secure_hash_key");
payButton.setProductionStatus(PRODUCTION);
payButton.setTransactionReferenceNumber(AppUtils.generateRandomNumber());
        payButton.setCustomerId("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");
```

---

## 💳 Create a Transaction

```java
payButton.createTransaction(new PayButton.PaymentTransactionCallback() {

    @Override
    public void onCardTransactionSuccess(SuccessfulCardTransaction cardTransaction) {
        paymentStatusTextView.setText(cardTransaction.toString());
        Log.v("Transaction Ref:", cardTransaction.SystemReference);
        Log.v("Customer Id:", cardTransaction.tokenCustomerId);
    }

    @Override
    public void onWalletTransactionSuccess(SuccessfulWalletTransaction walletTransaction) {
        paymentStatusTextView.setText(walletTransaction.toString());
    }

    @Override
    public void onError(TransactionException error) {
        paymentStatusTextView.setText("Failed: " + error.getMessage());
    }
});
```

---

## 📊 Callback Details

### ✅ `onCardTransactionSuccess`
Returns:
- NetworkReference
- AuthCode
- ActionCode
- ReceiptNumber
- Amount

---

### ✅ `onWalletTransactionSuccess`
Returns:
- NetworkReference
- Amount

---

### ❌ `onError`
Returns:
- Exception with error details

---

## ⚠️ Dependency Conflict Resolution

If you encounter version conflicts (e.g., Gson):

```gradle
configurations.all {
    resolutionStrategy {
        force 'com.google.code.gson:gson:2.8.4'
    }
}
```

---

## 🧱 Built With

- **Retrofit** – Networking
- **EventBus** – Event communication

---

## ✍️ Author

**PaySky Company**  
🌐 https://www.paysky.io

---

## 🔗 Links

- Website: https://www.paysky.io
- LinkedIn: https://www.linkedin.com/company/paysky