package com.revenuecat.purchases_flutter;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;
import com.revenuecat.purchases.Entitlement;
import com.revenuecat.purchases.PurchaserInfo;
import com.revenuecat.purchases.Purchases;
import com.revenuecat.purchases.PurchasesError;
import com.revenuecat.purchases.interfaces.GetSkusResponseListener;
import com.revenuecat.purchases.interfaces.MakePurchaseListener;
import com.revenuecat.purchases.interfaces.ReceiveEntitlementsListener;
import com.revenuecat.purchases.interfaces.ReceivePurchaserInfoListener;
import com.revenuecat.purchases.interfaces.UpdatedPurchaserInfoListener;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.view.FlutterNativeView;

import static com.revenuecat.purchases_flutter.Mappers.mapEntitlements;
import static com.revenuecat.purchases_flutter.Mappers.mapPurchaserInfo;
import static com.revenuecat.purchases_flutter.Mappers.mapSkuDetails;

/** PurchasesFlutterPlugin */
public class PurchasesFlutterPlugin implements MethodCallHandler {

  private static final String PURCHASER_INFO_UPDATED = "Purchases-PurchaserInfoUpdated";

  private final Activity activity;
  private final Context context;
  private final MethodChannel channel;

  public PurchasesFlutterPlugin(Registrar registrar, MethodChannel channel) {
    this.activity = registrar.activity();
    this.context = registrar.context();
    this.channel = channel;
    registrar.addViewDestroyListener(new PluginRegistry.ViewDestroyListener() {
      @Override
      public boolean onViewDestroy(FlutterNativeView flutterNativeView) {
        onDestroy();
        return false;
      }
    });
  }

  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "purchases_flutter");
    channel.setMethodCallHandler(new PurchasesFlutterPlugin(registrar, channel));
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    switch (call.method) {
    case "setupPurchases":
      String apiKey = call.argument("apiKey");
      String appUserId = call.argument("appUserId");
      Boolean observerMode = call.argument("observerMode");
        setupPurchases(apiKey, appUserId, observerMode, result);
      break;
    case "setAllowSharingStoreAccount":
      Boolean allowSharing = call.argument("allowSharing");
      setAllowSharingAppStoreAccount(allowSharing, result);
      break;
    case "addAttributionData":
      Map<String, String> data = call.argument("data");
      int network = call.argument("network") != null ? (int) call.argument("network") : -1;
      String networkUserId = call.argument("networkUserId");
      addAttributionData(data, network, networkUserId);
      break;
    case "getEntitlements":
      getEntitlements(result);
      break;
    case "getProductInfo":
      ArrayList<String> productIdentifiers = call.argument("productIdentifiers");
      String type = call.argument("type");
      getProductInfo(productIdentifiers, type, result);
      break;
    case "makePurchase":
      String productIdentifier = call.argument("productIdentifier");
      ArrayList<String> oldSKUs = call.argument("oldSKUs");
      String type1 = call.argument("type");
      makePurchase(productIdentifier, oldSKUs, type1, result);
      break;
    case "getAppUserID":
      getAppUserID(result);
      break;
    case "restoreTransactions":
      restoreTransactions(result);
      break;
    case "reset":
      reset(result);
      break;
    case "identify":
      String appUserID = call.argument("appUserID");
      identify(appUserID, result);
      break;
    case "createAlias":
      String newAppUserID = call.argument("newAppUserID");
      createAlias(newAppUserID, result);
      break;
    case "setDebugLogsEnabled":
      boolean enabled = call.argument("enabled") != null && (boolean) call.argument("enabled");
      setDebugLogsEnabled(enabled, result);
      break;
    case "getPurchaserInfo":
      getPurchaserInfo(result);
      break;
    case "syncPurchases":
      syncPurchases(result);
      break;
    case "setAutomaticAttributionCollection":
      break;
    default:
      result.notImplemented();
      break;
    }
  }

  private void onDestroy() {
    Purchases.getSharedInstance().close();
  }

  private void sendEvent(String eventName, @Nullable Map<String, Object> params) {
    channel.invokeMethod(eventName, params);
  }

  private void setupPurchases(String apiKey, String appUserID, @Nullable Boolean observerMode, final Result result) {
    if (observerMode != null) {
      Purchases.configure(this.context, apiKey, appUserID, observerMode);
    } else {
      Purchases.configure(this.context, apiKey, appUserID);
    }
    Purchases.getSharedInstance().setUpdatedPurchaserInfoListener(new UpdatedPurchaserInfoListener() {
      @Override
      public void onReceived(@NonNull PurchaserInfo purchaserInfo) {
        sendEvent(PURCHASER_INFO_UPDATED, mapPurchaserInfo(purchaserInfo));
      }
    });
    result.success(null);
  }

  private void setAllowSharingAppStoreAccount(boolean allowSharingAppStoreAccount, Result result) {
    Purchases.getSharedInstance().setAllowSharingPlayStoreAccount(allowSharingAppStoreAccount);
    result.success(null);
  }

  private void addAttributionData(Map<String, String> data, int network, @Nullable String networkUserId) {
    for (Purchases.AttributionNetwork attributionNetwork : Purchases.AttributionNetwork.values()) {
      if (attributionNetwork.getServerValue() == network) {
        Purchases.addAttributionData(data, attributionNetwork, networkUserId);
      }
    }
  }

  private void getEntitlements(final Result result) {
    Purchases.getSharedInstance().getEntitlements(new ReceiveEntitlementsListener() {
      @Override
      public void onReceived(@NonNull Map<String, Entitlement> entitlementMap) {
        result.success(mapEntitlements(entitlementMap));
      }

      @Override
      public void onError(@NonNull PurchasesError error) {
        reject(result, error);
      }
    });
  }

  private void getProductInfo(ArrayList<String> productIDs, String type, final Result result) {
    GetSkusResponseListener listener = new GetSkusResponseListener() {
      @Override
      public void onReceived(@NonNull List<SkuDetails> skus) {
        ArrayList<Map> products = new ArrayList<>();
        for (SkuDetails detail : skus) {
          products.add(mapSkuDetails(detail));
        }

        result.success(products);
      }

      @Override
      public void onError(@NonNull PurchasesError error) {
        reject(result, error);
      }

    };

    if (type.toLowerCase().equals("subs")) {
      Purchases.getSharedInstance().getSubscriptionSkus(productIDs, listener);
    } else {
      Purchases.getSharedInstance().getNonSubscriptionSkus(productIDs, listener);
    }
  }

  private void makePurchase(final String productIdentifier, ArrayList<String> oldSkus, String type,
      final Result result) {
    Purchases.getSharedInstance().makePurchase(this.activity, productIdentifier, type, oldSkus,
        new MakePurchaseListener() {
          @Override
          public void onCompleted(@NonNull Purchase purchase, @NonNull PurchaserInfo purchaserInfo) {
            Map<String, Object> map = new HashMap<>();
            map.put("productIdentifier", purchase.getSku());
            map.put("purchaserInfo", mapPurchaserInfo(purchaserInfo));
            result.success(map);
          }

          @Override
          public void onError(@NonNull PurchasesError error, Boolean userCancelled) {
            reject(result, error);
          }
        });
  }

  private void getAppUserID(final Result result) {
    result.success(Purchases.getSharedInstance().getAppUserID());
  }

  private void restoreTransactions(final Result result) {
    Purchases.getSharedInstance().restorePurchases(new ReceivePurchaserInfoListener() {
      @Override
      public void onReceived(@NonNull PurchaserInfo purchaserInfo) {
        result.success(mapPurchaserInfo(purchaserInfo));
      }

      @Override
      public void onError(@NonNull PurchasesError error) {
        reject(result, error);
      }
    });
  }

  private void reset(final Result result) {
    Purchases.getSharedInstance().reset(new ReceivePurchaserInfoListener() {
      @Override
      public void onReceived(@NonNull PurchaserInfo purchaserInfo) {
        result.success(mapPurchaserInfo(purchaserInfo));
      }

      @Override
      public void onError(@NonNull PurchasesError error) {
        reject(result, error);
      }
    });
  }

  private void identify(String appUserID, final Result result) {
    Purchases.getSharedInstance().identify(appUserID, new ReceivePurchaserInfoListener() {
      @Override
      public void onReceived(@NonNull PurchaserInfo purchaserInfo) {
        result.success(mapPurchaserInfo(purchaserInfo));
      }

      @Override
      public void onError(@NonNull PurchasesError error) {
        reject(result, error);
      }
    });
  }

  private void createAlias(String newAppUserID, final Result result) {
    Purchases.getSharedInstance().createAlias(newAppUserID, new ReceivePurchaserInfoListener() {
      @Override
      public void onReceived(@NonNull PurchaserInfo purchaserInfo) {
        result.success(mapPurchaserInfo(purchaserInfo));
      }

      @Override
      public void onError(@NonNull PurchasesError error) {
        reject(result, error);
      }
    });
  }

  private void setDebugLogsEnabled(boolean enabled, final Result result) {
    Purchases.setDebugLogsEnabled(enabled);
    result.success(null);
  }

  private void getPurchaserInfo(final Result result) {
    Purchases.getSharedInstance().getPurchaserInfo(new ReceivePurchaserInfoListener() {
      @Override
      public void onReceived(@NonNull PurchaserInfo purchaserInfo) {
        result.success(mapPurchaserInfo(purchaserInfo));
      }

      @Override
      public void onError(@NonNull PurchasesError error) {
        reject(result, error);
      }
    });
  }

  private void syncPurchases(final Result result) {
    Purchases.getSharedInstance().syncPurchases();
    result.success(null);
  }

  private static void reject(Result result, PurchasesError error) {
    Map<String, String> userInfoMap = new HashMap<>();
    userInfoMap.put("message", error.getMessage());
    userInfoMap.put("readable_error_code", error.getCode().name());
    if (error.getUnderlyingErrorMessage() != null && !error.getUnderlyingErrorMessage().isEmpty()) {
      userInfoMap.put("underlyingErrorMessage", error.getUnderlyingErrorMessage());
    }
    result.error(error.getCode().ordinal() + "", error.getMessage(), userInfoMap);
  }
}