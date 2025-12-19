package azinomotosanda.cmd_Reader;

// 必要なパーツ（クラス）をインポート
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.util.*; // 面倒だから全部インポートしちゃう

// メインクラス。コマンドもイベントもTab補完も全部ここでやる「欲張りセット」
public final class cmd_Reader extends JavaPlugin implements CommandExecutor, Listener, TabCompleter {

    // プレイヤーが「今どのアイテムの、何ページ目を見てるか」をメモしておく場所
    // UUIDはプレイヤーの固有番号。さすがに知ってるよね～
    HashMap<UUID, SessionData> userData = new HashMap<>();

    // Tab補完を早くするために、全アイテム名をここに突っ込んでおくリスト
    List<String> cacheMatNames = new ArrayList<>();

    // プラグインが起動したときに1回だけ動くやつ
    @Override
    public void onEnable() {
        // 全部のアイテムをループして、リストに名前を詰め込む
        for (Material m : Material.values()) {
            if (m.isItem()) {
                cacheMatNames.add(m.name().toLowerCase()); // 小文字にして入れとく
            }
        }
        // ABC順に並び替え
        Collections.sort(cacheMatNames);

        // "cmd" コマンドを使えるように登録。
        // getCommandがnullじゃないかチェックしないとエラー吐くことがあるので一応チェック
        if (getCommand("cmd") != null) {
            getCommand("cmd").setExecutor(this);    // コマンド処理は俺に任せろ
            getCommand("cmd").setTabCompleter(this); // Tab補完も俺に任せろ
        }

        // イベント（クリックとか）を聞き取る耳を登録
        getServer().getPluginManager().registerEvents(this, this);

        // 起動ログ
        getLogger().info("cmd_Reader 起動！");
    }

    // --- Tabキーを押したときの予測変換 ---
    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        // 引数が1個目（/cmd <ココ>）のときだけ働く
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            // ユーザーが入力しかけた文字(args[0])とマッチするやつを探してlistに入れる
            StringUtil.copyPartialMatches(args[0], cacheMatNames, list);
            return list;
        }
        return null; // それ以外は何もしない
    }

    // --- /cmd コマンドを打ったときの処理 ---
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        // コマンド打ったのがプレイヤーじゃなかったら弾く
        if (!(s instanceof Player)) {
            s.sendMessage("コンソールからは無理ですよ、お兄さん。");
            return true;
        }

        Player p = (Player) s; // 扱いやすいように Player型に変換

        // 引数が空っぽだったら怒る
        if (args.length == 0) {
            p.sendMessage(Component.text("/cmd <アイテム名>", NamedTextColor.RED));
            return true;
        }

        // 入力された名前からマテリアルを探す（大文字小文字は気にしない）
        String inputName = args[0].toUpperCase();
        Material mat = Material.matchMaterial(inputName);

        // そんなアイテムねーよって時
        if (mat == null) {
            p.sendMessage(Component.text("アイテムが存在しません: " + args[0], NamedTextColor.RED));
            return true;
        }

        // データを保存（最初は0ページ目から）
        userData.put(p.getUniqueId(), new SessionData(mat, 0));

        // GUIを開く処理へ飛ばす
        openGui(p);
        return true;
    }

    // --- GUI（インベントリ）を開く処理 ---
    // ここでガラス板とか並べる
    private void openGui(Player p) {
        // さっき保存したデータを取り出す
        SessionData data = userData.get(p.getUniqueId());
        if (data == null) return; // データなかったら帰る（念のため）

        // GUIの箱を作る。サイズは54（6行）。タイトルにページ数とかなんやらを入れる
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Viewer: " + data.mat.name() + " (P." + (data.page + 1) + ")"));

        // このページのスタート地点の番号（CMD）を計算
        // 1ページあたり45個表示する計算
        int startId = (data.page * 45) + 1;

        // 0番～44番のスロットにアイテムを埋めていくループ
        for (int i = 0; i < 45; i++) {
            int cmdId = startId + i;

            // 表示用アイテム作成
            ItemStack item = new ItemStack(data.mat);
            ItemMeta meta = item.getItemMeta();

            // metaがnullじゃないなら設定（空気とかnullになるけど一応）
            if (meta != null) {
                meta.setCustomModelData(cmdId); // ここが重要！CMD番号をセット
                meta.displayName(Component.text("CMD: " + cmdId, NamedTextColor.GREEN)); // 名前を緑色に

                // 説明文（Lore）をつける
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("クリックで入手", NamedTextColor.YELLOW));
                meta.lore(lore);

                item.setItemMeta(meta);
            }
            // インベントリに配置
            inv.setItem(i, item);
        }

        // --- 下の段（操作パネル）を作る ---
        // 毎回ItemStack作るの面倒だけんども、メソッド分けるほどでもないのでべた書きしちゃう
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE); // 背景のグレーガラス
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.text(" ")); // 名前を消す
        bg.setItemMeta(bgMeta);

        // 45～53番をグレーガラスで埋める
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, bg);
        }

        // 前のページボタン
        if (data.page > 0) {
            ItemStack prev = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta im = prev.getItemMeta();
            im.displayName(Component.text("<< 前のページ", NamedTextColor.RED));
            prev.setItemMeta(im);
            inv.setItem(45, prev); // 左下に置く
        }

        // 次のページボタン
        ItemStack next = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.displayName(Component.text("次のページ >>", NamedTextColor.GREEN));
        next.setItemMeta(nextMeta);
        inv.setItem(53, next); // 右下に置く

        // プレイヤーにｍ
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
    }

    // --- インベントリをクリックしたときの処理 ---
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // クリックしたのがプレイヤーじゃなかったら無視
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        // 開いてる画面のタイトルチェック。「Viewer: 」で始まってなかったら、わてのGUIじゃない
        String title = e.getView().getTitle();
        if (!title.startsWith("Viewer: ")) return;

        e.setCancelled(true); // 基本的にアイテムは動かせないようにする

        // データを取得
        SessionData data = userData.get(p.getUniqueId());
        if (data == null) return;

        int slot = e.getRawSlot(); // どこをクリックしたか（番号）
        ItemStack item = e.getCurrentItem(); // 何をクリックしたか

        // アイテムエリア（0～44）をクリックしたとき
        if (slot >= 0 && slot < 45) {
            // アイテムがあって、空気じゃないなら
            if (item != null && item.getType() != Material.AIR) {
                // プレイヤーにコピーを渡す
                p.getInventory().addItem(item.clone());
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1f);
            }
        }
        // 「前のページ」ボタン（左下：45番）
        else if (slot == 45 && data.page > 0) {
            data.page--; // ページを戻す
            openGui(p);  // 開き直す
        }
        // 「次のページ」ボタン（右下：53番）
        else if (slot == 53) {
            data.page++; // ページを進める
            openGui(p);  // 開き直す
        }
    }

    // データをまとめておくためのクラス
    // 下の方にちっちゃく書いとく
    class SessionData {
        Material mat;
        int page;
        public SessionData(Material m, int p) {
            this.mat = m;
            this.page = p;
        }
    }
}