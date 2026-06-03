# Préparation et Conception du Module de Scan Wi-Fi

Ce document détaille la démarche de conception, l'API proposée et les stratégies d'implémentation natives (Android et iOS) pour ajouter une fonctionnalité de scan Wi-Fi à notre module React Native `react-native-echo-mob`.

---

## 1. Objectif du Module
Fournir une méthode asynchrone qui effectue un scan des réseaux Wi-Fi disponibles à proximité et renvoie les résultats sous forme d'un objet contenant la liste des réseaux détectés avec leurs caractéristiques (SSID, BSSID, puissance du signal, etc.).

---

## 2. Définition de l'API TypeScript

Nous définissons le modèle de données retourné par le scan et l'interface du TurboModule.

### Modèles de Données (`src/types.ts`)
```typescript
export interface WifiNetwork {
  ssid: string;        // Nom du réseau (SSID)
  bssid: string;       // Adresse MAC de la borne (BSSID)
  rssi: number;        // Puissance du signal en dBm (ex: -65)
  frequency: number;   // Fréquence du canal en MHz (ex: 2412 pour du 2.4GHz)
  level: number;       // Qualité du signal calculée (0 à 4 ou pourcentage)
  capabilities: string; // Capacités/Sécurité (WPA2-PSK, WPA3, etc.)
}

export interface WifiScanResponse {
  success: boolean;
  networks: WifiNetwork[];
  error?: string;
}
```

### Spécification TurboModule (`src/NativeEchoMob.ts`)
```typescript
import { TurboModuleRegistry, type TurboModule } from 'react-native';
import type { WifiScanResponse } from './types';

export interface Spec extends TurboModule {
  multiply(a: number, b: number): number;
  getDayGreeting(n: number): string;
  
  // Nouvelle méthode asynchrone pour scanner le Wi-Fi
  startWifiScan(): Promise<WifiScanResponse>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('EchoMob');
```

---

## 3. Stratégie d'Implémentation Android

Sur Android, le scan Wi-Fi s'effectue via le service système `WifiManager`.

### A. Permissions requises (`AndroidManifest.xml`)
Pour scanner le Wi-Fi, l'application doit disposer de permissions de localisation (car l'identifiant SSID permet potentiellement de déduire la position géographique) et de gestion du Wi-Fi :
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

### B. Mécanisme dans le Module Native (Kotlin)
1. **Accès au WifiManager** :
   ```kotlin
   val wifiManager = reactApplicationContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
   ```
2. **Démarrage du scan** :
   Appeler `wifiManager.startScan()`. 
3. **Réception des résultats (BroadcastReceiver)** :
   Le scan étant asynchrone, il faut enregistrer un `BroadcastReceiver` écoutant l'action `WifiManager.SCAN_RESULTS_AVAILABLE_ACTION`.
4. **Extraction des résultats** :
   Une fois le scan fini, récupérer `wifiManager.scanResults`, formater chaque résultat dans un `WritableMap` et résoudre la promesse JS avec un `WritableArray` de réseaux.

---

## 4. Stratégie d'Implémentation iOS (Limitations et Solutions)

> [!WARNING]
> **Limitations d'iOS (Confidentialité d'Apple)** :
> Contrairement à Android, iOS n'autorise pas les applications tierces à effectuer un scan actif de tous les réseaux Wi-Fi environnants (SSID/BSSID voisins) via des API publiques pour des raisons de protection de la vie privée.

### Solutions et Alternatives sur iOS :
1. **Récupération du Réseau Actuel uniquement** :
   Nous pouvons utiliser le framework `NetworkExtension` et la classe `NEHotspotNetwork` pour récupérer les détails du Wi-Fi auquel l'appareil est actuellement connecté.
   - **Conditions requises** :
     - Activer la capability **Access Wi-Fi Information** dans le projet Xcode.
     - Demander la permission de géolocalisation à l'utilisateur (`CoreLocation`).
2. **Retour d'une réponse simulée ou vide** :
   Si aucun réseau actuel n'est connecté ou si le scan complet est demandé, retourner une réponse structurée avec un tableau vide ou uniquement l'élément connecté pour éviter de faire crasher l'application.

### Code iOS suggéré (`ios/EchoMob.mm`) :
```objc
#import <NetworkExtension/NetworkExtension.h>
#import <CoreLocation/CoreLocation.h>

RCT_EXPORT_METHOD(startWifiScan:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    // Sur iOS, nous récupérons le réseau actuel si disponible
    [NEHotspotNetwork fetchCurrentWithCompletionHandler:^(NEHotspotNetwork * _Nullable currentNetwork) {
        NSMutableArray *networksArray = [[NSMutableArray alloc] init];
        
        if (currentNetwork != nil) {
            NSDictionary *networkDict = @{
                @"ssid": currentNetwork.SSID ?: @"",
                @"bssid": currentNetwork.BSSID ?: @"",
                @"rssi": @(currentNetwork.signalStrength * -100), // Estimation
                @"frequency": @(2400),
                @"level": @(currentNetwork.signalStrength * 4),
                @"capabilities": @""
            };
            [networksArray addObject:networkDict];
        }
        
        resolve(@{
            @"success": @(YES),
            @"networks": networksArray
        });
    }];
}
```

---

## 5. Exemple d'Utilisation dans React Native

```typescript
import React, { useEffect, useState } from 'react';
import { Button, Text, View, PermissionsAndroid, Platform } from 'react-native';
import { startWifiScan, type WifiNetwork } from 'react-native-echo-mob';

export default function WifiScanner() {
  const [networks, setNetworks] = useState<WifiNetwork[]>([]);

  const requestPermissions = async () => {
    if (Platform.OS === 'android') {
      const granted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
      );
      return granted === PermissionsAndroid.RESULTS.GRANTED;
    }
    return true; // Géré par CoreLocation sur iOS
  };

  const handleScan = async () => {
    const hasPermission = await requestPermissions();
    if (!hasPermission) {
      console.warn("Permission de localisation refusée");
      return;
    }

    try {
      const result = await startWifiScan();
      if (result.success) {
        setNetworks(result.networks);
      } else {
        console.error("Échec du scan :", result.error);
      }
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <View style={{ padding: 20 }}>
      <Button title="Scanner le Wi-Fi" onPress={handleScan} />
      {networks.map((net, i) => (
        <Text key={i}>{net.ssid} ({net.rssi} dBm)</Text>
      ))}
    </View>
  );
}
```
