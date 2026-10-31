## Vorace 0.9.1 : les menus aux couleurs de Vorace

Les menus de `/vorace` prennent l'identité de Vorace, le dégradé violet → rose de son nom.

- **Fond dessiné** : cadre en dégradé, bandeau de titre violet-rose, cases sombres pile sous les cases du coffre et de l'inventaire, ligne de navigation en rose. Vorace dessine ces fonds lui-même et les ajoute au pack (rien à installer).
- **Boutons** : noms des boutons principaux en dégradé, descriptions en lavande, lignes de clic en rose (« ▶ Clic » puis l'effet).
- Plus de vitres grises : la ligne du bas ne garde que Retour, les pages et Fermer.
- `menus.fond-dessine: false` dans la config remet les coffres normaux du jeu (avec des vitres violettes et roses).
- Les fonds sont aussi des glyphes (`vorace_fond_1` à `vorace_fond_6`) : `/vorace glyphe menu vorace_fond_6` montre l'aperçu. Ils n'apparaissent pas dans les listes de glyphes.

> **Code écrit par une IA.** Vorace est développé par Claude (l'IA d'Anthropic), à la demande et sous la direction de ORA, qui teste et décide de ce qui est ajouté. Le code est ouvert pour que chacun puisse le relire, le corriger et l'améliorer.
