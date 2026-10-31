// Faux joueur pour l'essai des menus (CI) : /vorace, accueil, objets (un objet reçu), modèles, retour.
import mineflayer from 'mineflayer';

const version = process.argv[2] || false;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Menus', auth: 'offline', version });
const titre = (w) => (typeof w.title === 'string' ? w.title : JSON.stringify(w.title));
const attendreFenetre = () => new Promise((ok, ko) => {
  const t = setTimeout(() => ko(new Error('pas de fenêtre')), 8000);
  bot.once('windowOpen', (w) => { clearTimeout(t); ok(w); });
});
const pause = (ms) => new Promise((r) => setTimeout(r, ms));
const fin = (code) => { bot.quit(); setTimeout(() => process.exit(code), 500); };

bot.once('spawn', async () => {
  try {
    await pause(3000);
    let w = attendreFenetre();
    bot.chat('/vorace');
    let f = await w;
    const accueil = titre(f);
    const boutons = f.slots.slice(0, f.inventoryStart).filter((s) => s).length;
    console.log('MENU ACCUEIL ' + accueil + ' boutons=' + boutons);
    w = attendreFenetre();
    await bot.clickWindow(10, 0, 0);
    f = await w;
    const objets = f.slots.slice(0, 45).filter((s) => s).length;
    console.log('MENU OBJETS ' + titre(f) + ' objets=' + objets);
    const avant = bot.inventory.items().length;
    await bot.clickWindow(0, 0, 0);
    await pause(1500);
    const recu = bot.inventory.items().length - avant;
    console.log('MENU RECU ' + recu);
    w = attendreFenetre();
    await bot.clickWindow(f.inventoryStart - 9, 0, 0); // « Retour »
    f = await w;
    console.log('MENU RETOUR ' + titre(f));
    w = attendreFenetre();
    await bot.clickWindow(28, 0, 0); // modèles animés
    f = await w;
    const modeles = f.slots.slice(0, 45).filter((s) => s).length;
    console.log('MENU MODELES ' + titre(f) + ' modeles=' + modeles);
    // fond dessiné : un caractère de glyphe (U+EA00 à U+F6FF) au début du titre
    const fond = /[\uEA00-\uF6FF]/.test(accueil) || /\\u(e[a-f]|f[0-6])[0-9a-f]{2}/i.test(accueil);
    console.log('MENU FOND ' + fond);
    const ok = fond && accueil.includes('Vorace') && boutons >= 10 && objets > 0 && recu > 0 && modeles > 0;
    console.log((ok ? 'MENUS OK' : 'MENUS ECHEC') + ` fond=${fond} boutons=${boutons} objets=${objets} recu=${recu} modeles=${modeles}`);
    fin(ok ? 0 : 1);
  } catch (e) {
    console.log('MENUS ECHEC ' + e.message);
    fin(1);
  }
});
bot.on('message', (m) => console.log('MENU MESSAGE ' + m.toString()));
bot.on('kicked', (r) => console.log('MENU EXPULSE ' + JSON.stringify(r)));
bot.on('error', (e) => console.log('MENU ERREUR ' + e));
setTimeout(() => { console.log('MENUS ECHEC trop long'); process.exit(1); }, 60000);
