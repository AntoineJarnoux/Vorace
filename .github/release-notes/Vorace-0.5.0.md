## Vorace 0.5.0 : glyphes, emojis et fonds de menus

Un glyphe est une image qui s'écrit comme une lettre. Ils se créent dans `plugins/Vorace/glyphes/`. Exemples créés au démarrage : cœur, étoile, sourire, feu et un fond de coffre.

- **Emojis dans le chat et sur les panneaux** : `:coeur:`, `<3`, `:)`... deviennent des images. Suggestions dans le chat avec la touche Tab, `/emojis` pour la liste (cliquable), permission par emoji possible.
- **Marchent partout** : noms d'objets, lore, Tab, tableau de scores, titres, bossbars, menus, même avec les plugins qui n'écrivent que du texte simple (les glyphes utilisent la police normale du jeu).
- Balises `<glyphe:coeur>` et `<decalage:-8>` dans les noms d'objets, le lore et les messages de Vorace.
- **Fonds de menus personnalisés** : un glyphe `fond-menu: true` se place tout seul en haut à gauche de la fenêtre, le titre reste à sa place. Aperçu : `/vorace glyphe menu fond_coffre`. Pour les autres plugins : `VoraceAPI.titreMenu(fond, titre)`.
- PlaceholderAPI (facultatif) : `%vorace_glyphe_<id>%`, `%vorace_decalage_<n>%`.
- `/vorace glyphe liste` : clic sur un glyphe pour copier son caractère et le coller dans n'importe quel texte.
- API : `VoraceAPI.glyphe`, `glypheComposant`, `decalage`, `titreMenu`, `emojis`.

Rien à changer dans tes fichiers : remplace le .jar et redémarre. Le fichier `glyphes/exemple-glyphes.yml` et ses images sont ajoutés une seule fois.

> **Code écrit par une IA.** Vorace est développé par Claude (l'IA d'Anthropic), à la demande et sous la direction de ORA, qui teste et décide de ce qui est ajouté. Le code est ouvert pour que chacun puisse le relire, le corriger et l'améliorer.
