package io.paysky.ui.fragment.paymentprocessing

import android.os.Bundle
import android.util.Log
import io.paysky.data.model.CardPaymentParameters
import io.paysky.data.model.PaymentData
import io.paysky.data.model.SuccessfulCardTransaction
import io.paysky.data.model.TokenizedCardPaymentParameters
import io.paysky.data.model.request.ManualPaymentRequest
import io.paysky.data.model.response.ManualPaymentResponse
import io.paysky.data.network.ApiConnection
import io.paysky.data.network.ApiLinks
import io.paysky.data.network.ApiResponseListener
import io.paysky.exception.TransactionException
import io.paysky.ui.mvp.BasePresenter
import io.paysky.util.AppConstant
import io.paysky.util.AppUtils
import io.paysky.util.HashGenerator
import io.paysky.util.TransactionManager
import io.paysky.util.parcelable

class PaymentProcessingPresenter(
    arguments: Bundle?,
    view: PaymentProcessingView
) :
    BasePresenter<PaymentProcessingView>() {
    private val paymentData: PaymentData?
    private val cardPayment: CardPaymentParameters?
    private val tokenizedCard: TokenizedCardPaymentParameters?

    init {
        paymentData = arguments?.parcelable(AppConstant.BundleKeys.PAYMENT_DATA)

        cardPayment = arguments?.parcelable(AppConstant.BundleKeys.CARD_DATA)
        tokenizedCard = arguments?.parcelable(AppConstant.BundleKeys.TOKENIZED_CARD)

        TransactionManager.setTransactionType(TransactionManager.TransactionType.MANUAL)
        attachView(view)

        if (cardPayment != null) {
            makePayment()
        } else {
            makeTokenizedCardPayment()
        }
    }

    private fun makeTokenizedCardPayment() {
        paymentData?.let { payment ->
            tokenizedCard?.let { tokenizedCardParams ->
                makeTokenizedPayment(
                    secureHash = payment.secureHashKey,
                    currencyCode = payment.currencyCode,
                    payAmount = payment.amountFormatted,
                    merchantId = payment.merchantId,
                    terminalId = payment.terminalId,
                    ccv = tokenizedCardParams.cvv,
                    cardId = tokenizedCardParams.TokenCardId,
                    customerId = payment.customerId,
                    customerSessionId = payment.customerSession
                )
            }
        }
    }

    private fun makeTokenizedPayment(
        secureHash: String,
        currencyCode: String,
        payAmount: String,
        merchantId: String,
        terminalId: String,
        ccv: String,
        cardId: Int,
        customerId: String,
        customerSessionId: String
    ) {
        // check internet.
        if (!view.isInternetAvailable) {
            view.showNoInternetDialog()
            return
        }
        view.showProgress()
        // create request.
        val paymentRequest = ManualPaymentRequest()
        val amount = AppUtils.formatPaymentAmountToServer(payAmount)
        paymentRequest.amountTrxn = amount.toString() + ""
        paymentRequest.cardAcceptorIDcode = merchantId
        paymentRequest.cardAcceptorTerminalID = terminalId
        paymentRequest.currencyCodeTrxn = currencyCode
        paymentRequest.cvv2 = ccv
        paymentRequest.iSFromPOS = false
        paymentRequest.systemTraceNr = paymentData?.transactionReferenceNumber
        paymentRequest.MerchantReference = paymentData?.transactionReferenceNumber
        paymentRequest.dateTimeLocalTrxn = AppUtils.getDateTimeLocalTrxn()
        paymentRequest.merchantId = merchantId
        paymentRequest.terminalId = terminalId
        paymentRequest.tokenCardId = cardId.toString()
        paymentRequest.tokenCustomerId = customerId
        paymentRequest.tokenCustomerSession = customerSessionId

        paymentRequest.returnURL = ApiLinks.PAYMENT_LINK
        // create secure hash.
        paymentRequest.secureHash = HashGenerator.encode(
            secureHash,
            paymentRequest.dateTimeLocalTrxn,
            merchantId,
            terminalId
        )
        // make transaction.
        ApiConnection.executePayment(
            paymentRequest,
            object : ApiResponseListener<ManualPaymentResponse?> {
                override fun onSuccess(response: ManualPaymentResponse?) {
                    if (isViewDetached) return
                    // server make response.
                    view.dismissProgress()
                    if (response?.challengeRequired == true) {
                        view.show3dpWebView(response.threeDSUrl, paymentData)
                    } else {
                        if (response?.mWActionCode != null) {
                            val transactionException = TransactionException()
                            transactionException.errorMessage = response.mWMessage
                            TransactionManager.setTransactionException(transactionException)
                            val bundle = Bundle()
                            bundle.putString(
                                AppConstant.BundleKeys.DECLINE_CAUSE,
                                response.mWMessage
                            )
                            bundle.putString("opened_by", "manual_payment")
                            view.showPaymentFailedFragment(bundle)
                        } else {
                            if (response?.actionCode == null || response.actionCode.isEmpty() || response.actionCode != "000") {
                                val transactionException = TransactionException()
                                transactionException.errorMessage = response?.message
                                TransactionManager.setTransactionException(transactionException)
                                val bundle = Bundle()
                                bundle.putString(
                                    AppConstant.BundleKeys.DECLINE_CAUSE,
                                    response?.message
                                )
                                bundle.putString("opened_by", "manual_payment")
                                view.showPaymentFailedFragment(bundle)
                            } else {
                                // transaction success.
                                val cardTransaction = SuccessfulCardTransaction()
                                cardTransaction.ActionCode = response.actionCode
                                cardTransaction.AuthCode = response.authCode
                                cardTransaction.MerchantReference = response.merchantReference
                                cardTransaction.Message = response.message
                                cardTransaction.NetworkReference = response.networkReference
                                cardTransaction.ReceiptNumber = response.receiptNumber
                                cardTransaction.SystemReference =
                                    response.systemReference.toString() + ""
                                cardTransaction.Success = response.success
                                cardTransaction.merchantId = paymentData?.merchantId
                                cardTransaction.terminalId = paymentData?.terminalId
                                cardTransaction.amount = paymentData?.executedTransactionAmount
                                cardTransaction.tokenCustomerId = paymentData?.customerId
                                TransactionManager.setCardTransaction(cardTransaction)
                                view.showTransactionApprovedFragment(
                                    transactionNo = response.transactionNo,
                                    authCode = response.authCode,
                                    receiptNumber = response.receiptNumber,
                                    cardHolder = "cardHolder",
                                    cardNumber = "cardNumber",
                                    systemReference = response.systemReference.toString() + "",
                                    paymentData = paymentData
                                )
                            }
                        }
                    }
                }

                override fun onFail(error: Throwable) {
                    // payment failed.
                    if (isViewDetached) return
                    view.dismissProgress()
                    //view.dismissProgress()
                    val transactionException = TransactionException()
                    transactionException.errorMessage = error.message
                    TransactionManager.setTransactionException(transactionException)
                    error.printStackTrace()
                    view.showErrorInServerToast()
                }
            })
    }

    private fun makePayment() {
        paymentData?.let { payment ->
            cardPayment?.let { cardData ->
                executeManualPayment(
                    secureHash = payment.secureHashKey,
                    currencyCode = payment.currencyCode,
                    payAmount = payment.amountFormatted,
                    merchantId = payment.merchantId,
                    terminalId = payment.terminalId,
                    ccv = cardData.cvv,
                    expiryDate = cardData.expireDate,
                    cardHolder = cardData.cardOwnerName,
                    cardNumber = cardData.cardNumber,
                    isSaveCard = cardData.isSaveCard,
                    isDefault = cardData.isDefault,
                    mobileNumber = payment.customerMobile,
                    email = payment.customerEmail,
                    customerId = payment.customerId,
                    customerSessionId = payment.customerSession
                )
            } ?: {
                Log.d("Make Payment", "makePayment: card payment null")
            }
        } ?: {
            Log.d("Make Payment", "makePayment: payment data null")
        }
    }


    private fun executeManualPayment(
        secureHash: String,
        currencyCode: String,
        payAmount: String,
        merchantId: String,
        terminalId: String,
        ccv: String,
        expiryDate: String,
        cardHolder: String,
        cardNumber: String,
        isSaveCard: Boolean,
        isDefault: Boolean,
        mobileNumber: String?,
        email: String?,
        customerId: String?,
        customerSessionId: String?
    ) {
        // check internet.
        if (!view.isInternetAvailable) {
            view.showNoInternetDialog()
            return
        }
        view.showProgress()
        // create request.
        val paymentRequest = ManualPaymentRequest()
        val amount = AppUtils.formatPaymentAmountToServer(payAmount)
        paymentRequest.amountTrxn = amount.toString() + ""
        paymentRequest.cardAcceptorIDcode = merchantId
        paymentRequest.cardAcceptorTerminalID = terminalId
        paymentRequest.currencyCodeTrxn = currencyCode
        paymentRequest.cvv2 = ccv
        paymentRequest.dateExpiration = expiryDate
        paymentRequest.iSFromPOS = false
        paymentRequest.pAN = cardNumber
        paymentRequest.systemTraceNr = paymentData?.transactionReferenceNumber
        paymentRequest.MerchantReference = paymentData?.transactionReferenceNumber
        paymentRequest.dateTimeLocalTrxn = AppUtils.getDateTimeLocalTrxn()
        paymentRequest.merchantId = merchantId
        paymentRequest.terminalId = terminalId

        paymentRequest.isSaveCard = isSaveCard
        paymentRequest.isDefaultCard = isDefault
        paymentRequest.mobileNo = mobileNumber
        paymentRequest.email = email
        paymentRequest.tokenCustomerId = customerId
        paymentRequest.tokenCustomerSession = customerSessionId
        paymentRequest.cardHolderName = cardHolder

        paymentRequest.returnURL = ApiLinks.PAYMENT_LINK
        // create secure hash.
        paymentRequest.secureHash = HashGenerator.encode(
            secureHash,
            paymentRequest.dateTimeLocalTrxn,
            merchantId,
            terminalId
        )
        // make transaction.
        ApiConnection.executePayment(
            paymentRequest,
            object : ApiResponseListener<ManualPaymentResponse?> {
                override fun onSuccess(response: ManualPaymentResponse?) {
                    if (isViewDetached) return
                    // server make response.
                    view.dismissProgress()
                    if (response?.challengeRequired == true) {
                        if (paymentData?.customerId == null) {
                            paymentData?.customerId = response.tokenCustomerId
                        }
                        view.show3dpWebView(response.threeDSUrl, paymentData)
                    } else {
                        if (response?.mWActionCode != null) {
                            val transactionException = TransactionException()
                            transactionException.errorMessage = response.mWMessage
                            TransactionManager.setTransactionException(transactionException)
                            val bundle = Bundle()
                            bundle.putString(
                                AppConstant.BundleKeys.DECLINE_CAUSE,
                                response.mWMessage
                            )
                            bundle.putString("opened_by", "manual_payment")
                            view.showPaymentFailedFragment(bundle)
                        } else {
                            if (response?.actionCode == null || response.actionCode.isEmpty() || response.actionCode != "000") {
                                val transactionException = TransactionException()
                                transactionException.errorMessage = response?.message
                                TransactionManager.setTransactionException(transactionException)
                                val bundle = Bundle()
                                bundle.putString(
                                    AppConstant.BundleKeys.DECLINE_CAUSE,
                                    response?.message
                                )
                                bundle.putString("opened_by", "manual_payment")
                                view.showPaymentFailedFragment(bundle)
                            } else {
                                // transaction success.
                                val cardTransaction = SuccessfulCardTransaction()
                                cardTransaction.ActionCode = response.actionCode
                                cardTransaction.AuthCode = response.authCode
                                cardTransaction.MerchantReference = response.merchantReference
                                cardTransaction.Message = response.message
                                cardTransaction.NetworkReference = response.networkReference
                                cardTransaction.ReceiptNumber = response.receiptNumber
                                cardTransaction.SystemReference =
                                    response.systemReference.toString() + ""
                                cardTransaction.Success = response.success
                                cardTransaction.merchantId = paymentData?.merchantId
                                cardTransaction.terminalId = paymentData?.terminalId
                                cardTransaction.amount = paymentData?.executedTransactionAmount
                                if (paymentData?.customerId != null) {
                                    cardTransaction.tokenCustomerId = paymentData.customerId
                                } else {
                                    paymentData?.customerId = response.tokenCustomerId
                                    cardTransaction.tokenCustomerId = response.tokenCustomerId
                                }
                                TransactionManager.setCardTransaction(cardTransaction)
                                view.showTransactionApprovedFragment(
                                    transactionNo = response.transactionNo,
                                    authCode = response.authCode,
                                    receiptNumber = response.receiptNumber,
                                    cardHolder = cardHolder,
                                    cardNumber = cardNumber,
                                    systemReference = response.systemReference.toString() + "",
                                    paymentData = paymentData
                                )
                            }
                        }
                    }
                }

                override fun onFail(error: Throwable) {
                    // payment failed.
                    if (isViewDetached) return
                    view.dismissProgress()
                    //view.dismissProgress()
                    val transactionException = TransactionException()
                    transactionException.errorMessage = error.message
                    TransactionManager.setTransactionException(transactionException)
                    error.printStackTrace()
                    view.showErrorInServerToast()
                }
            })
    }
}