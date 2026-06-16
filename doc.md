# react-native-echo-mob

> Librairie React Native (Turbo Module) permettant le scan de réseaux Wi-Fi et la connexion à un réseau depuis une application mobile cross-platform (Android & iOS).

---

## Table des matières

1. [Installation](#installation)
2. [Configuration](#configuration)
   - [Android](#configuration-android)
   - [iOS](#configuration-ios)
3. [API Reference](#api-reference)
   - [multiply](#multiply)
   - [getDayGreeting](#getdaygreeting)
   - [WifiModule](#wifimodule)
     - [requestPermissions](#requestpermissions)
     - [checkWifiEnabled](#checkwifienabled)
     - [scanWifiNetworks](#scanwifinetworks)
     - [connectToWifi](#connecttowifi)
4. [Types](#types)
5. [Codes d'erreur](#codes-derreur)
6. [Comportement par plateforme](#comportement-par-plateforme)
   - [Android](#android)
   - [iOS](#ios)
7. [Exemples complets](#exemples-complets)
8. [Architecture interne](#architecture-interne)

---

## Installation

```bash
# avec npm
npm install react-native-echo-mob

# avec yarn
yarn add react-native-echo-mob
```

### iOS — installation des dépendances CocoaPods

```bash
cd ios && pod install
```

---

## Configuration

### Configuration Android

Ajoutez les permissions suivantes dans votre `AndroidManifest.xml` :

```xml
<!-- Localisation (obligatoire pour le scan Wi-Fi depuis Android 6) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Lecture et modification de l'état Wi-Fi -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

> **Note :** Le module demande automatiquement ces permissions lors du premier appel à une méthode Wi-Fi. Il est cependant recommandé d'appeler `requestPermissions()` explicitement lors du processus d'onboarding.

### Configuration iOS

1. **Entitlement Wi-Fi** — Activez la capacité **Access WiFi Information** dans Xcode :
   - Ouvrez votre projet Xcode
   - Onglet `Signing & Capabilities`
   - Cliquez `+ Capability` → sélectionnez `Access WiFi Information`

2. **`Info.plist`** — Ajoutez la description d'usage pour la localisation (requise par Apple pour accéder au SSID) :

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>Cette app utilise votre localisation pour identifier le réseau Wi-Fi connecté.</string>
```

3. **`NEHotspotConfiguration`** (connexion réseau) — Ajoutez l'entitlement :

```xml
<!-- ios/<MonApp>.entitlements -->
<key>com.apple.developer.networking.HotspotConfiguration</key>
<true/>
```

---

## API Reference

### `multiply`

Multiplie deux nombres entre eux. Fonction utilitaire synchrone.

```typescript
import { multiply } from 'react-native-echo-mob';

const result = multiply(3, 7); // → 21
```

**Signature :**

```typescript
function multiply(a: number, b: number): number
```

| Paramètre | Type     | Description         |
|-----------|----------|---------------------|
| `a`       | `number` | Premier opérande    |
| `b`       | `number` | Deuxième opérande   |

**Retour :** `number` — Le produit `a × b`.

---

### `getDayGreeting`

Retourne un message de salutation pour le jour de la semaine correspondant à un entier.

```typescript
import { getDayGreeting } from 'react-native-echo-mob';

getDayGreeting(0);  // → "bonjour dimanche"
getDayGreeting(1);  // → "bonjour lundi"
getDayGreeting(8);  // → "bonjour lundi"  (8 % 7 = 1)
getDayGreeting(-1); // → "bonjour samedi" (géré correctement)
```

**Signature :**

```typescript
function getDayGreeting(n: number): string
```

| Paramètre | Type     | Description                                         |
|-----------|----------|-----------------------------------------------------|
| `n`       | `number` | Entier quelconque (le modulo 7 est appliqué en interne) |

**Retour :** `string` — Chaîne de la forme `"bonjour <jour>"`.

**Correspondance des index :**

| Index (n % 7) | Jour       |
|---------------|------------|
| 0             | dimanche   |
| 1             | lundi      |
| 2             | mardi      |
| 3             | mercredi   |
| 4             | jeudi      |
| 5             | vendredi   |
| 6             | samedi     |

---

### `WifiModule`

Module principal exposant les fonctionnalités de scan et de connexion Wi-Fi.

```typescript
import { WifiModule } from 'react-native-echo-mob';
```

---

#### `requestPermissions`

Demande explicitement toutes les permissions Wi-Fi + Localisation requises.

> **Optionnel** — chaque méthode Wi-Fi demande automatiquement les permissions si elles sont manquantes. Utile pour un écran d'onboarding.

```typescript
const status = await WifiModule.requestPermissions();
// → "granted" | "denied" | "never_ask_again"
```

**Signature :**

```typescript
WifiModule.requestPermissions(): Promise<PermissionStatus>
```

**Retour :** [`Promise<PermissionStatus>`](#permissionstatus)

---

#### `checkWifiEnabled`

Vérifie si le Wi-Fi est activé sur l'appareil.

```typescript
const isEnabled = await WifiModule.checkWifiEnabled();
if (!isEnabled) {
  console.warn('Veuillez activer le Wi-Fi.');
}
```

**Signature :**

```typescript
WifiModule.checkWifiEnabled(): Promise<boolean>
```

**Retour :** `Promise<boolean>` — `true` si le Wi-Fi est activé, `false` sinon.

---

#### `scanWifiNetworks`

Lance un scan des réseaux Wi-Fi disponibles et retourne la liste des points d'accès détectés.

```typescript
try {
  const networks = await WifiModule.scanWifiNetworks();
  networks.forEach(net => {
    console.log(`${net.ssid} — ${net.signalLevel} dBm`);
    if (net.isCached) {
      console.log('⚠️ Résultats mis en cache (throttle Android)');
    }
  });
} catch (error) {
  // error.code: 'PERMISSION_DENIED' | 'WIFI_DISABLED' | 'SCAN_FAILED'
  console.error(error.code, error.message);
}
```

**Signature :**

```typescript
WifiModule.scanWifiNetworks(): Promise<WifiNetwork[]>
```

**Retour :** [`Promise<WifiNetwork[]>`](#wifinetwork)

**Erreurs possibles :**

| Code              | Cause                                          |
|-------------------|------------------------------------------------|
| `PERMISSION_DENIED` | Permissions refusées par l'utilisateur        |
| `WIFI_DISABLED`    | Le Wi-Fi est désactivé sur l'appareil          |
| `SCAN_FAILED`      | Scan échoué et aucun résultat en cache         |

---

#### `connectToWifi`

Connecte l'appareil à un réseau Wi-Fi.

```typescript
// Réseau sécurisé WPA2
await WifiModule.connectToWifi('MonReseau', 'motdepasse123');

// Réseau ouvert (sans mot de passe)
await WifiModule.connectToWifi('ReseauPublic');

// Réseau caché
await WifiModule.connectToWifi('ReseauCache', 'motdepasse', { hidden: true });
```

**Signature :**

```typescript
WifiModule.connectToWifi(
  ssid: string,
  password?: string,
  options?: ConnectOptions
): Promise<void>
```

| Paramètre  | Type             | Requis | Description                                      |
|------------|------------------|--------|--------------------------------------------------|
| `ssid`     | `string`         | ✅      | SSID du réseau cible                             |
| `password` | `string`         | ❌      | Mot de passe WPA/WPA2 (omettre pour réseau ouvert)|
| `options`  | `ConnectOptions` | ❌      | Options supplémentaires (ex. `hidden`)            |

**Erreurs possibles :**

| Code               | Cause                                              |
|--------------------|----------------------------------------------------|
| `PERMISSION_DENIED`  | Permissions refusées                             |
| `WIFI_DISABLED`     | Wi-Fi désactivé                                   |
| `INVALID_SSID`      | SSID vide ou null                                 |
| `CONNECT_FAILED`    | Connexion échouée (API legacy < Android 10)       |
| `SUGGESTION_FAILED` | Suggestion réseau refusée (Android 10+)           |

---

## Types

### `WifiNetwork`

Représente un réseau Wi-Fi détecté lors d'un scan.

```typescript
interface WifiNetwork {
  /** Nom du réseau (SSID) */
  ssid: string;

  /** Adresse MAC du point d'accès (BSSID) */
  bssid: string;

  /** Force du signal en dBm (valeur négative ; plus proche de 0 = meilleur signal) */
  signalLevel: number;

  /** Fréquence en MHz (≈2400 pour 2.4 GHz, ≈5000 pour 5 GHz) */
  frequency: number;

  /** Type de sécurité du réseau */
  security: WifiSecurity;

  /**
   * Android uniquement.
   * true si les résultats proviennent du cache OS (throttle Android 9+).
   * Dans ce cas, vérifier `timestamp` pour évaluer la fraîcheur.
   */
  isCached: boolean;

  /** Timestamp Unix (ms) du moment où le scan a été déclenché */
  timestamp: number;
}
```

### `WifiSecurity`

```typescript
type WifiSecurity = 'OPEN' | 'WEP' | 'WPA' | 'WPA2' | 'WPA3';
```

### `PermissionStatus`

```typescript
type PermissionStatus = 'granted' | 'denied' | 'never_ask_again';
```

### `ConnectOptions`

```typescript
interface ConnectOptions {
  /** true si le réseau est caché (SSID non diffusé). Défaut : false */
  hidden?: boolean;
}
```

---

## Codes d'erreur

Tous les rejets de promesses exposent un objet avec les propriétés `code` et `message` :

```typescript
try {
  await WifiModule.scanWifiNetworks();
} catch (err: any) {
  console.log(err.code);    // ex. "WIFI_DISABLED"
  console.log(err.message); // ex. "Wi-Fi is disabled on this device."
}
```

| Code                | Message par défaut                                           |
|---------------------|--------------------------------------------------------------|
| `PERMISSION_DENIED`  | Required permissions are not granted.                       |
| `WIFI_DISABLED`      | Wi-Fi is disabled on this device.                           |
| `SCAN_FAILED`        | Wi-Fi scan failed or returned no results.                   |
| `CONNECT_FAILED`     | Failed to connect to the Wi-Fi network.                     |
| `SUGGESTION_FAILED`  | WifiNetworkSuggestion could not be added.                   |
| `ALREADY_CONNECTED`  | Already connected to this network.                          |
| `INVALID_SSID`       | The provided SSID is null or empty.                         |
| `UNKNOWN`            | An unknown error occurred.                                  |

---

## Comportement par plateforme

### Android

| Fonctionnalité       | API < 29 (Android < 10)              | API ≥ 29 (Android 10+)                        |
|----------------------|--------------------------------------|------------------------------------------------|
| **Scan**             | Résultats frais, non throttlé        | Throttlé (≈4 scans/2 min en foreground)        |
| **Connexion**        | `WifiConfiguration` (immédiate)      | `WifiNetworkSuggestion` (persistante, OS-gérée)|
| **isCached**         | Toujours `false`                     | `true` si throttlé                            |

#### Gestion du throttle (Android 9+ / API 28+)

Le système Android limite les scans à **environ 4 appels par 2 minutes** en foreground et **1 appel par 30 minutes** en background. Lorsque la limite est atteinte :

- `isCached: true` est retourné sur chaque réseau du tableau
- Le champ `timestamp` indique l'heure du dernier scan réussi

**Recommandation :** vérifiez toujours `isCached` avant d'afficher les résultats et indiquez à l'utilisateur si les données sont potentiellement obsolètes.

```typescript
const networks = await WifiModule.scanWifiNetworks();
const isStale = networks.some(n => n.isCached);
const ageMs = Date.now() - networks[0]?.timestamp;

if (isStale && ageMs > 60_000) {
  console.warn('Résultats vieux de plus d\'une minute (throttle OS)');
}
```

#### Connexion sur Android 10+ (`WifiNetworkSuggestion`)

- La suggestion est **persistante** : elle survit au redémarrage de l'appareil.
- La promesse se résout dès que la suggestion est **acceptée par l'OS**, pas quand la connexion est effective.
- L'OS se connecte automatiquement au réseau suggéré quand il est à portée.

### iOS

| Fonctionnalité   | Comportement                                                     |
|------------------|------------------------------------------------------------------|
| **Scan**         | Retourne uniquement le réseau actuellement connecté (1 item max) |
| **Connexion**    | Utilise `NEHotspotConfiguration`                                 |
| **isCached**     | Toujours `false`                                                 |
| **Permissions**  | Entitlement `Access WiFi Information` requis                    |

> Apple interdit le scan complet des réseaux Wi-Fi pour les applications publiques. `scanWifiNetworks()` retourne uniquement le SSID du réseau actuellement connecté.

---

## Exemples complets

### Exemple 1 — Onboarding avec demande de permissions

```tsx
import React, { useEffect, useState } from 'react';
import { View, Text, Button } from 'react-native';
import { WifiModule, PermissionStatus } from 'react-native-echo-mob';

export function OnboardingScreen() {
  const [status, setStatus] = useState<PermissionStatus | null>(null);

  const handleRequest = async () => {
    const result = await WifiModule.requestPermissions();
    setStatus(result);
  };

  return (
    <View>
      <Text>Statut des permissions : {status ?? 'non demandées'}</Text>
      <Button title="Autoriser le Wi-Fi" onPress={handleRequest} />
    </View>
  );
}
```

### Exemple 2 — Scanner et afficher les réseaux

```tsx
import React, { useState } from 'react';
import { View, Text, FlatList, Button, ActivityIndicator } from 'react-native';
import { WifiModule, WifiNetwork } from 'react-native-echo-mob';

export function WifiScannerScreen() {
  const [networks, setNetworks] = useState<WifiNetwork[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const scan = async () => {
    setLoading(true);
    setError(null);
    try {
      const results = await WifiModule.scanWifiNetworks();
      setNetworks(results);
    } catch (err: any) {
      setError(`[${err.code}] ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <View>
      <Button title="Scanner" onPress={scan} disabled={loading} />
      {loading && <ActivityIndicator />}
      {error && <Text style={{ color: 'red' }}>{error}</Text>}
      <FlatList
        data={networks}
        keyExtractor={(item) => item.bssid}
        renderItem={({ item }) => (
          <View>
            <Text>{item.ssid} ({item.security})</Text>
            <Text>Signal : {item.signalLevel} dBm — {item.frequency} MHz</Text>
            {item.isCached && <Text>⚠️ Données en cache</Text>}
          </View>
        )}
      />
    </View>
  );
}
```

### Exemple 3 — Se connecter à un réseau

```tsx
import React, { useState } from 'react';
import { View, TextInput, Button, Alert } from 'react-native';
import { WifiModule } from 'react-native-echo-mob';

export function ConnectScreen() {
  const [ssid, setSsid] = useState('');
  const [password, setPassword] = useState('');

  const connect = async () => {
    try {
      await WifiModule.connectToWifi(ssid, password || undefined);
      Alert.alert('Succès', `Connexion à "${ssid}" en cours...`);
    } catch (err: any) {
      Alert.alert('Erreur', `[${err.code}] ${err.message}`);
    }
  };

  return (
    <View>
      <TextInput placeholder="SSID" value={ssid} onChangeText={setSsid} />
      <TextInput
        placeholder="Mot de passe"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
      />
      <Button title="Connexion" onPress={connect} />
    </View>
  );
}
```

### Exemple 4 — Utilisation de `multiply` et `getDayGreeting`

```typescript
import { multiply, getDayGreeting } from 'react-native-echo-mob';

// Multiplication
console.log(multiply(6, 7));      // 42

// Jour de la semaine
console.log(getDayGreeting(1));   // "bonjour lundi"
console.log(getDayGreeting(15));  // "bonjour lundi" (15 % 7 = 1)
```

---

## Architecture interne

```
react-native-echo-mob/
├── src/                         # Sources TypeScript (JS layer)
│   ├── index.tsx                # Point d'entrée — réexporte multiply, getDayGreeting, WifiModule
│   ├── multiply.tsx             # Implémentation JS de multiply
│   ├── getDayGreeting.tsx       # Implémentation JS de getDayGreeting
│   ├── NativeEchoMob.ts         # Spécification TurboModule (Codegen)
│   └── wifi/
│       └── index.ts             # WifiModule — types + wrapper natif
│
├── android/                     # Implémentation native Android (Kotlin)
│   └── src/main/java/com/echomob/
│       ├── EchoMobModule.kt     # Module principal RN (multiply, getDayGreeting)
│       ├── EchoMobPackage.kt    # Enregistrement du module dans React Native
│       ├── WifiModule.kt        # Logique Wi-Fi (scan, connexion, permissions)
│       └── wifi/
│           ├── NetworkUtils.kt  # Utilitaires (ScanResult → Map, connexion réseau)
│           └── WifiError.kt     # Enum des codes d'erreur
│
├── ios/                         # Implémentation native iOS (Objective-C++)
│   ├── EchoMob.h                # En-tête du module
│   └── EchoMob.mm               # Implémentation (multiply, getDayGreeting, TurboModule bridge)
│
├── lib/                         # Sortie compilée (générée par react-native-builder-bob)
├── example/                     # Application d'exemple
└── EchoMob.podspec              # Spécification CocoaPods pour iOS
```

### Flux d'appel (exemple : `scanWifiNetworks`)

```
JS (WifiModule.scanWifiNetworks())
  └─► NativeModules.WifiModule.scanWifiNetworks()
        ├─► [Android] WifiModule.kt::scanWifiNetworks()
        │     └─► ensurePermissionsThen → doScan()
        │           └─► BroadcastReceiver SCAN_RESULTS_AVAILABLE
        │                 └─► NetworkUtils.scanResultToMap() → Promise.resolve([...])
        └─► [iOS] EchoMob.mm (Wi-Fi limité par Apple)
              └─► Retourne le SSID connecté uniquement
```

---

## Compatibilité

| Plateforme | Version minimale |
|------------|-----------------|
| Android    | API 21 (Android 5.0 Lollipop) |
| iOS        | Minimum iOS défini par `min_ios_version_supported` (podspec) |
| React Native | 0.71+ (New Architecture / TurboModules) |

---

## Licence

MIT — voir [LICENSE](./LICENSE)

---

## Auteur

**marc** — [marc.wogue@gmail.com](mailto:marc.wogue@gmail.com)  
GitHub : [@marcwogue](https://github.com/marcwogue)  
Issues : [github.com/marcwogue/react-native-echo-mob/issues](https://github.com/marcwogue/react-native-echo-mob/issues)
