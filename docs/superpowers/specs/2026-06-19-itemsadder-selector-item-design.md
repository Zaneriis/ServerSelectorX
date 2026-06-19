# Item du selector forcé à un item ItemsAdder

**Date:** 2026-06-19
**Branche:** free
**Statut:** Approuvé

## Objectif

Forcer l'item du server selector à être l'item custom ItemsAdder `mcicons:icon_ender_chest`,
en dur dans le code source. Le champ `item:` des fichiers de menu n'est plus utilisé pour
déterminer l'item du selector.

## Contexte

Le champ `item:` de SSX n'accepte qu'un nom de `Material` Bukkit (ou un préfixe `head:<uuid>`).
Un item ItemsAdder (`mcicons:icon_ender_chest`) est un item vanilla avec un CustomModelData géré
par ItemsAdder ; il ne peut donc pas être représenté par un simple nom de Material. Il faut passer
par l'API ItemsAdder (`CustomStack`) pour le créer et le détecter.

L'item du selector intervient à 3 endroits dans le code :
1. **Donner l'item au join** — `OnJoinListener` → `Main.getItemFromMaterialString`
2. **Détecter le clic droit** pour ouvrir le menu — `SelectorOpenListener` (compare le `Material` en main)
3. **bStats** — `Stats.java` lit `config.getString("item")` (déjà null-safe, ligne 28 : `if (material == null) continue;`)

## Décisions

- **Hardcodé en dur** : l'id ItemsAdder est une constante dans le code, le champ `item:` du yml est ignoré pour le selector.
- **Accès API via dépendance Maven `provided`** : `com.github.LoneDev6:api-itemsadder` via JitPack (non bundlée), pas de réflexion. (Le repo historique `repo.devs.beer` est injoignable — DNS HS ; JitPack est le canal officiel. Version `3.6.1`, déjà en cache `~/.m2`. Package Java inchangé : `dev.lone.itemsadder.api`.)

## Modifications

### 1. `pom.xml`

Ajouter le repository et la dépendance (scope `provided`, non shadée) :

```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>
```

```xml
<dependency>
  <groupId>com.github.LoneDev6</groupId>
  <artifactId>api-itemsadder</artifactId>
  <version>3.6.1</version>
  <scope>provided</scope>
</dependency>
```

La dépendance est `provided` : elle n'est ni bundlée ni relocalisée par le shade plugin
(comme spigot-api et placeholderapi).

### 2. `plugin.yml`

Ajouter `ItemsAdder` aux soft-dépendances pour garantir que ItemsAdder charge ses items
avant l'activation de SSX :

```yaml
softdepend: [PlaceholderAPI, ItemsAdder]
```

### 3. `Main.java`

Ajouter une constante publique et un helper centralisé de construction de l'item du selector :

```java
public static final String SELECTOR_IA_ID = "mcicons:icon_ender_chest";

/**
 * Construit l'item ItemsAdder du selector. Renvoie un fallback COMPASS (avec un warning
 * dans les logs) si ItemsAdder est absent ou si l'item n'est pas encore chargé.
 */
static ItemBuilder getSelectorItem(final Player player) {
    final CustomStack stack = CustomStack.getInstance(SELECTOR_IA_ID);
    if (stack == null) {
        Main.getPlugin().getLogger().warning(
            "ItemsAdder item '" + SELECTOR_IA_ID + "' introuvable (ItemsAdder absent ou pas encore chargé). Fallback sur COMPASS.");
        return new ItemBuilder(Material.COMPASS);
    }
    return new ItemBuilder(stack.getItemStack());
}
```

`getItemFromMaterialString` reste inchangé : il continue de servir aux items affichés *dans*
les menus (sections `online`/`offline`/`dynamic`), qui ne sont pas concernés par cette demande.

### 4. `OnJoinListener.java`

Remplacer la construction de l'item depuis le champ `item:` par l'helper hardcodé :

- Avant : `Main.getItemFromMaterialString(player, config.getString("item"))`
- Après : `Main.getSelectorItem(player)`

Le `item-name` et le `item-lore` du yml continuent de s'appliquer (`.coloredName(...)`,
`.coloredLore(...)`). Le reste de la logique (`inv-slot`, `only-in-worlds`, slot `-1` auto) est conservé.

### 5. `SelectorOpenListener.java`

Remplacer la comparaison de `Material` par une détection ItemsAdder. À la place de :

```java
final String string = config.getString("item");
Material material = Material.getMaterial(string);
if (material == null) material = Material.STONE;
if (player.getInventory().getItemInMainHand().getType() != material) continue;
```

utiliser :

```java
final CustomStack held = CustomStack.byItemStack(player.getInventory().getItemInMainHand());
final boolean isSelector = held != null && held.getNamespacedID().equals(Main.SELECTOR_IA_ID);
if (!isSelector) continue;
```

Comportement conservé : on parcourt les configs, on saute celles dont `item:` vaut `NONE`
(opt-out), on vérifie la permission via `SelectorMenu.checkPermission`, et on ouvre le premier
menu éligible. Pour le cas d'un selector unique (default.yml), cela ouvre ce menu.

### 6. `resources/default.yml`

Mettre à jour le commentaire au-dessus du champ `item:` pour signaler qu'il est désormais
ignoré pour le selector (l'item est forcé à `mcicons:icon_ender_chest` en dur). Le champ est
conservé pour compatibilité et pour l'opt-out `NONE`.

## Hors périmètre

- `getItemFromMaterialString` : inchangé (items des menus).
- `Stats.java` : inchangé (déjà null-safe).
- Aucun nouveau champ de configuration : l'id ItemsAdder est volontairement en dur.
- Pas de support d'item IA configurable (décision : hardcode).

## Gestion des erreurs

- ItemsAdder absent / item pas encore chargé → `CustomStack.getInstance` renvoie `null` →
  fallback `COMPASS` + warning log. Le plugin ne crash pas.
- Détection (`CustomStack.byItemStack`) renvoie `null` pour les items non-IA → traité comme
  « pas le selector » (pas d'ouverture de menu).

## Vérification

Pas de suite de tests dans ce projet. Vérification manuelle :
1. `mvn clean package` réussit (build avec la dépendance `provided`).
2. Sur un serveur Spigot 1.13+ avec ItemsAdder + le pack `mcicons` chargé :
   - Au join, le joueur reçoit l'item `mcicons:icon_ender_chest` avec le nom/lore configurés.
   - Clic droit en tenant cet item → le menu s'ouvre.
   - Tenir un autre item → le menu ne s'ouvre pas.
3. Sans ItemsAdder installé : warning dans les logs, fallback COMPASS, pas de crash.
