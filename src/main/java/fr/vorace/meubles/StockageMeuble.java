package fr.vorace.meubles;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Coffre d'un meuble (le contenu est rangé dans les données du meuble). */
public final class StockageMeuble implements InventoryHolder {

    final UUID meuble;
    Inventory inventaire;

    StockageMeuble(UUID meuble) {
        this.meuble = meuble;
    }

    @Override
    public Inventory getInventory() {
        return inventaire;
    }
}
