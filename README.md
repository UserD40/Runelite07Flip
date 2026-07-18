# 07Flip — GE Flip Finder

![07Flip](images/banner.png)

A RuneLite plugin that surfaces live Grand Exchange flipping data from [07flip.com](https://07flip.com) inside your client: top flips, dump warnings, per-item insights, premium merch alerts, Moon and Barrows armour repair flips, potion decanting profit, plus a local trade tracker with automatic price entry on the GE setup screen — all in one sidebar panel.

## ✅ No account required for the basics

Most of the plugin works out of the box. An API key from [07flip.com](https://07flip.com) unlocks the premium tabs and optional trade syncing.

| Feature | Free | API key |
|---|:-:|:-:|
| Flips, Dumps, Item, Decant tabs | ✅ | ✅ |
| All Grand Exchange overlays and auto price-fill | ✅ | ✅ |
| Local My Trades history | ✅ | ✅ |
| Merch Alerts (premium) | — | ✅ |
| Moon / Barrows armour repair flips | — | ✅ |
| My Trades website sync | — | ✅ |

To get an API key: sign up at [07flip.com](https://07flip.com), log in with Discord, click your Discord icon (top-right) and select **View API Key**. Paste it into the plugin's General config section.

**No player data is ever sent to 07flip.com.** The optional My Trades website sync transmits only item ID, quantity and price — your account name is never included.

## ✨ Sidebar panel

Open the panel from the **07Flip** icon in the RuneLite sidebar. Every tab can be hidden individually in config.

- **Flips** — the most profitable GE flips right now: recommended buy/sell, profit after 2% tax, ROI and buy limit. Left-click a row for the Item tab, right-click for guided **Buy/Sell on GE**.
- **Dumps** — items just heavily dumped by big traders, often temporarily below their normal range.
- **Item** — full per-item analysis: live buy/sell, a 24h buy/sell sparkline, GE volume and active alerts (free); recommended prices, score, range and projection (premium).
- **Alerts** *(premium)* — conviction-tier merch alerts with target/current/start price, hold time and a sparkline. A **Successful** sub-tab (free to view) tracks alerts that hit target.
- **Moon** / **Barrows** *(premium)* — pieces and sets profitable to buy broken, repair at the POH armour stand, and resell. Net profit uses your configured Smithing level.
- **Decant** — the most profitable potion (4)→(3)→(2)→(1) decanting opportunities across the live GE market.
- **My Trades** — records every GE buy/sell that completes while the plugin runs: live offer progress, FIFO-matched profit per flip, stats (total/today/best/worst, win rate, ROI, tax paid) and activity. Stored locally; persists across sessions. With an API key and **Share trade data** opted in, completed trades also sync to your website account (item ID + qty + price only).

## 🎯 Grand Exchange integration

Layered directly into the GE interface; none require an API key.

- **Movable price overlay** on the setup screen — right-click either price to fill it into the custom price input.
- **Guided Buy/Sell on GE** — highlights the next empty slot, types the item name on click, glows the *Enter price* button, and fills the recommended price when you open the custom-price input.
- **Frozen sell prices** — queuing a buy pins the recommended sell at that moment; if the market drifts down before your buy completes, the frozen price still appears so you don't undercut your margin. Clears once the position is sold.
- **Inventory tooltip** — hover an item for recommended sell, your FIFO cost basis and live ROI (green in profit, red underwater).
- **GP drop animation** — a fading +X / −X gp number on each completed offer.

## ⚙️ Configuration

Open the RuneLite config (wrench icon) → **07Flip**, grouped into General, Panel tabs, Tab order, Grand Exchange integration, and Trade tracker sections. Key options: API key, refresh interval (60–600 s), Smithing level (repair cost), personalised flips by cash stack, per-tab visibility, GE overlay toggles, and opt-in website sync.

## 🚀 Getting started

1. Install **07Flip** from the RuneLite Plugin Hub.
2. Click the **07Flip** icon in the sidebar — the Flips tab opens by default.
3. *(Optional)* Sign up at [07flip.com](https://07flip.com) for an API key to unlock Alerts, Moon, Barrows and trade syncing.
4. Right-click any flip → **Buy on GE** to be guided into the offer setup with the recommended price ready.

## 🔗 Links

- Website: [07flip.com](https://07flip.com)
- Discord: [Join our community](https://discord.gg/xQaYM9TaMr)

## 📝 License

BSD 2-Clause — see [LICENSE](LICENSE).
