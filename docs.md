# Documentation de la démarche d'ajout de la méthode `getDayGreeting`

Ce document explique les étapes suivies pour ajouter la nouvelle méthode `getDayGreeting` au module React Native `react-native-echo-mob`.

## 1. Expression du besoin
L'objectif est d'ajouter une méthode qui prend en entrée un nombre `n` et renvoie une salutation `"bonjour [jour]"` où le jour est déterminé par `n % 7`.
La correspondance définie pour la semaine est la suivante (avec une normalisation pour gérer les nombres négatifs) :
- `0` : dimanche
- `1` : lundi
- `2` : mardi
- `3` : mercredi
- `4` : jeudi
- `5` : vendredi
- `6` : samedi

---

## 2. Étapes de l'implémentation

### A. Définition de la spécification TurboModule (Codegen)
Dans les modules React Native modernes (utilisant la New Architecture / TurboModules), l'interface native est définie en TypeScript.

Fichier modifié : `src/NativeEchoMob.ts`
- Ajout de la déclaration de la méthode `getDayGreeting(n: number): string;` dans la spécification `Spec` héritant de `TurboModule`.

```typescript
export interface Spec extends TurboModule {
  multiply(a: number, b: number): number;
  getDayGreeting(n: number): string;
}
```

### B. Implémentation du fallback JavaScript
Pour les environnements non-natifs (comme le web ou lors des tests unitaires simulés), nous définissons une implémentation JS standard.

Nouveau fichier créé : `src/getDayGreeting.tsx`
- Implémentation de la fonction qui calcule `((Math.floor(n) % 7) + 7) % 7` pour obtenir un index sûr compris entre 0 et 6, puis renvoie `"bonjour " + jour`.

```typescript
export function getDayGreeting(n: number): string {
  const days = [
    'dimanche',
    'lundi',
    'mardi',
    'mercredi',
    'jeudi',
    'vendredi',
    'samedi',
  ];
  const index = ((Math.floor(n) % 7) + 7) % 7;
  return `bonjour ${days[index]}`;
}
```

### C. Implémentation de la passerelle native (Native Bridge)
Pour cibler l'API native générée par Codegen sur iOS et Android :

Nouveau fichier créé : `src/getDayGreeting.native.tsx`
- Il redirige simplement l'appel vers l'instance native `EchoMob`.

```typescript
import EchoMob from './NativeEchoMob';

export function getDayGreeting(n: number): string {
  return EchoMob.getDayGreeting(n);
}
```

### D. Exportation du module
Fichier modifié : `src/index.tsx`
- Ajout de l'export de la nouvelle méthode afin qu'elle soit publiquement accessible par les utilisateurs de la bibliothèque :

```typescript
export { getDayGreeting } from './getDayGreeting';
```

### E. Implémentation Native pour Android (Kotlin)
Fichier modifié : `android/src/main/java/com/echomob/EchoMobModule.kt`
- Redéfinition (override) de la fonction `getDayGreeting(n: Double): String` imposée par l'interface parente générée de la spécification.
- Normalisation du modulo de la même façon en convertissant la valeur vers un entier.

```kotlin
  override fun getDayGreeting(n: Double): String {
    val days = arrayOf("dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi")
    val index = ((n.toInt() % 7) + 7) % 7
    return "bonjour ${days[index]}"
  }
```

### F. Implémentation Native pour iOS (Objective-C++)
Fichier modifié : `ios/EchoMob.mm`
- Ajout de l'implémentation de la méthode `- (NSString *)getDayGreeting:(double)n`.
- Utilisation de `NSArray` et de `NSString stringWithFormat:` pour renvoyer la chaîne saluant l'utilisateur.

```objc
- (NSString *)getDayGreeting:(double)n {
    NSArray *days = @[@"dimanche", @"lundi", @"mardi", @"mercredi", @"jeudi", @"vendredi", @"samedi"];
    int index = (((int)n % 7) + 7) % 7;
    return [NSString stringWithFormat:@"bonjour %@", days[index]];
}
```

---

## 3. Tests et Vérifications

1. **Compilation TypeScript & Validation Types** :
   Exécution réussie de la vérification de type statique :
   ```bash
   yarn typecheck
   ```
2. **Build du Package** :
   Exécution réussie du build de distribution avec `bob build` :
   ```bash
   yarn prepare
   ```
3. **Tests de Logique unitaire** :
   - Création de tests unitaires dans `src/__tests__/getDayGreeting.test.tsx`.
   - Test d'intégration rapide via Node.js sur le code compilé (`lib/module/getDayGreeting.js`) avec diverses valeurs positives, négatives et modulo 7 :
     ```bash
     node -e "const { getDayGreeting } = require('./lib/module/getDayGreeting'); console.log(getDayGreeting(1));"
     # Affiche bien : "bonjour lundi"
     ```
4. **Mise à jour de l'application exemple** :
   - Mise à jour de `example/src/App.tsx` pour importer et afficher le résultat de la fonction `getDayGreeting(1)`.
