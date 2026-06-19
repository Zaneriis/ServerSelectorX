# Item du selector via un item ItemsAdder (champ `ia-item` configurable)

**Date:** 2026-06-19
**Branche:** itemsadder-selector-item
**Statut:** Implémenté

## Objectif

Permettre que l'item du server selector soit un item custom ItemsAdder, configurable par
menu via un nouveau champ `ia-item`. Si ItemsAdder est installé et que l'item existe, il est
utilisé ; sinon SSX retombe (fallback) sur le champ vanilla `item:` existant.

Exemple (`default.yml`) :

```yaml
item: COMPASS
ia-item: mcicons:icon_ender_chest
```

> **Note d'historique :** une première version de ce spec forçait l'item *en dur*
> (`mcicons:icon_ender_chest` hardcodé, champ `item:` ignoré). Cette décision a été
> remplacée par l'approche configurable avec fallback décrite ici.

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

- **Configurable, pas hardcodé** : nouveau champ `ia-item` (id ItemsAdder namespacé) lu par menu.
- **Fallback sur `item:`** : si `ia-item` est absent, ou si ItemsAdder n'est pas chargé, ou si
  l'item n'existe pas / n'est pas encore chargé → on utilise le champ vanilla `item:` comme avant.
- **Accès API via dépendance Maven `provided`** : `com.github.LoneDev6:api-itemsadder` via JitPack
  (non bundlée), pas de réflexion. (Le repo historique `repo.devs.beer` est injoignable — DNS HS ;
  JitPack est le canal officiel. Version `3.6.1`, déjà en cache `~/.m2`. Package Java : `dev.lone.itemsadder.api`.)

## Modifications

### 1. `pom.xml`

Ajouter le repository JitPack et la dépendance (scope `provided`, non shadée) :

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

Deux helpers symétriques (construction + détection), partageant la même logique
`ia-item` sinon fallback `item:` :

```java
/**
 * Construit l'item du selector pour un menu. Si la config déclare un `ia-item`
 * (id ItemsAdder, ex. `mcicons:icon_ender_chest`) qui résout, cet item custom est
 * utilisé. Sinon (pas d'`ia-item`, ItemsAdder absent, ou item pas encore chargé)
 * fallback sur le Material vanilla `item:`.
 */
static ItemBuilder getSelectorItem(final Player player, final FileConfiguration config) {
    final String iaId = config.getString("ia-item");
    if (iaId != null && !iaId.isEmpty()) {
        final CustomStack stack = CustomStack.getInstance(iaId);
        if (stack != null) {
            return new ItemBuilder(stack.getItemStack());
        }
        getPlugin().getLogger().warning("ItemsAdder item '" + iaId
                + "' introuvable (ItemsAdder absent ou pas encore charge). Fallback sur le champ 'item'.");
    }
    return getItemFromMaterialString(player, config.getString("item"));
}

/**
 * Indique si `stack` est l'item du selector d'un menu. Miroir de getSelectorItem :
 * si `ia-item` est défini et résolvable, compare l'id ItemsAdder de l'item en main ;
 * sinon compare le Material vanilla `item:`.
 */
static boolean isSelectorItem(final ItemStack stack, final FileConfiguration config) {
    final String iaId = config.getString("ia-item");
    if (iaId != null && !iaId.isEmpty() && CustomStack.getInstance(iaId) != null) {
        final CustomStack held = CustomStack.byItemStack(stack);
        return held != null && held.getNamespacedID().equals(iaId);
    }

    if (!config.isString("item") || config.getString("item").equalsIgnoreCase("NONE")) {
        return false;
    }

    Material material = Material.getMaterial(config.getString("item"));
    if (material == null) {
        material = Material.STONE;
    }
    return stack.getType() == material;
}
```

`getItemFromMaterialString` reste inchangé : il continue de servir aux items affichés *dans*
les menus (sections `online`/`offline`/`dynamic`), qui ne sont pas concernés par cette demande.

### 4. `OnJoinListener.java`

Remplacer la construction de l'item depuis le champ `item:` par l'helper :

- Avant : `Main.getItemFromMaterialString(player, config.getString("item"))`
- Après : `Main.getSelectorItem(player, config)`

Le `item-name` et le `item-lore` du yml continuent de s'appliquer (`.coloredName(...)`,
`.coloredLore(...)`). Le reste de la logique (`inv-slot`, `only-in-worlds`, slot `-1` auto) est conservé.

### 5. `SelectorOpenListener.java`

Remplacer toute la logique de matching par `Material` par un appel à `Main.isSelectorItem` :

```java
if (!Main.isSelectorItem(player.getInventory().getItemInMainHand(), config)) {
    continue;
}
```

L'opt-out `item: NONE` reste géré (dans `isSelectorItem`, branche fallback). L'import
`org.bukkit.Material` devient inutilisé et est supprimé. On vérifie ensuite la permission
via `SelectorMenu.checkPermission`, puis on ouvre le premier menu éligible.

### 6. `resources/default.yml`

Documenter les deux champs : `item` (fallback vanilla) et `ia-item` (item ItemsAdder optionnel) :

```yaml
# The name of the server selector item (vanilla Material, or head:<uuid|auto>).
# Used as a fallback when 'ia-item' is unset or cannot be resolved.
item: COMPASS
# Optional: an ItemsAdder custom item (namespaced id) to use for the selector instead.
# If ItemsAdder is installed and this item exists, it is used; otherwise SSX falls back
# to the 'item' above. Remove this line to always use the vanilla 'item'.
ia-item: mcicons:icon_ender_chest
```

## Hors périmètre

- `getItemFromMaterialString` : inchangé (items des menus).
- `Stats.java` : inchangé (déjà null-safe).

## Gestion des erreurs

- ItemsAdder absent / item pas encore chargé → `CustomStack.getInstance` renvoie `null` →
  fallback sur le champ `item:` + warning log. Le plugin ne crash pas.
- Détection (`CustomStack.byItemStack`) renvoie `null` pour les items non-IA → traité comme
  « pas le selector » quand `ia-item` est actif ; sinon comparaison vanilla.

## Vérification

Pas de suite de tests dans ce projet. Vérification :
1. `mvn clean package` réussit (build avec la dépendance `provided`) → **BUILD SUCCESS**,
   jar produit, et aucune classe `dev/lone`/ItemsAdder dans le jar shadé.
2. Sur un serveur Spigot 1.13+ avec ItemsAdder + le pack `mcicons` chargé :
   - Au join, le joueur reçoit l'item `mcicons:icon_ender_chest` avec le nom/lore configurés.
   - Clic droit en tenant cet item → le menu s'ouvre.
   - Tenir un autre item → le menu ne s'ouvre pas.
3. Sans ItemsAdder installé (ou `ia-item` retiré) : fallback sur `item: COMPASS`, pas de crash.
