## Vorace 0.2.2 : correctif des objets

- **Corrigé** : au démarrage, l'enregistrement des recettes échouait (`NoSuchMethodError` sur `Registry.get`) dès qu'un objet avait des enchantements ou des attributs. `/vorace give` sur ces objets plantait aussi.
- Un enchantement, un attribut ou une recette illisible n'empêche plus jamais le reste des objets de fonctionner : il est juste signalé dans la console.

Rien à changer dans tes fichiers : remplace simplement le .jar et redémarre.

> **Code écrit par une IA.** Vorace est développé par Claude (l'IA d'Anthropic), à la demande et sous la direction de ORA, qui teste et décide de ce qui est ajouté. Le code est ouvert pour que chacun puisse le relire, le corriger et l'améliorer.
