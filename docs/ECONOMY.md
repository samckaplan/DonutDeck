# Economy and pricing model

DonutDeck is a calculator and order desk, not a price oracle. It has exactly three
kinds of prices, and only two of them are allowed to affect money decisions.

| Source | Meaning | Drives valuation? | Drives invest tickets? |
|---|---|---|---|
| `LIVE` | Returned by the official DonutSMP auction API | Yes | Yes |
| `MANUAL` | Entered by you after checking in game | Yes | Yes |
| `SAMPLE` | Zero-price placeholder from the built-in item list | No | No |

The built-in item list is deliberately **price-less**. It exists so the Market tab has
recognizable item names and icons before your first API refresh. Sample prices are hidden
by default and can never be used as a valuation or a ticket price.

> Prices on DonutSMP move constantly. This document contains formulas and hypothetical
> examples only. It does not claim that any item is worth a particular amount.

---

## 1. Where quotes come from

### LIVE quotes

The official API is documented at `https://api.donutsmp.net/doc.json`.

- `GET /v1/auction/list/{page}` returns current auction listings.
  An optional JSON body can contain `{"search": "...", "sort": "lowest_price"}`.
- `GET /v1/auction/transactions/{page}` returns recent completed sales, newest
  first. DonutDeck requests the first five pages per refresh.

Authentication:

```http
Authorization: Bearer <your /api key>
```

The key is created in game with **`/api`**. DonutDeck sends it only to
`api.donutsmp.net`, and only on a market refresh.

DonutDeck requests up to 18 item searches plus five transaction pages per refresh. With
the default 90-second interval that is roughly 15 requests/minute, well below the
documented 250 requests/minute/key limit. The refresh interval has a 30-second floor, and
manual refreshes have an 8-second cooldown.

For each item, the API gives:

- a lowest current ask (`price / count`, using the listing's total price and quantity);
- recent completed sale prices (`price / count`), filtered to the last 24 hours when the
  timestamp is present.

### MANUAL quotes

Open **Market → Set price**, or run:

```
/donutdeck price netherite_ingot 20m
```

This stores the value as `price`, `lowAsk`, and `medianSold`, with source `MANUAL`. It is
a planning input, not a claim that the item is currently trading at that value. Re-check
it when the market moves.

### SAMPLE placeholders

The seed list (elytra, netherite items, heads, totems, etc.) starts at zero. The
**Show sample badges** setting only controls whether these rows are visible; it does not
give them a price and does not make them trusted.

---

## 2. Quote math

For a quote with a lowest ask `A` and a median recent sale `S`:

```
bestPrice = 0.7 * S + 0.3 * A     if both S > 0 and A > 0
bestPrice = S                     if only S > 0
bestPrice = A                     if only A > 0
bestPrice = 0                     otherwise
```

Why blend? The lowest ask is what you can buy at right now; the median sale is what
buyers have actually been paying. A 70/30 blend leans toward completed sales while still
respecting the current ask. It is a heuristic, not financial advice.

`changePct` is computed from DonutDeck's own price history. It is labelled **CHG**, not
"24h", because a newly observed item may have less than 24 hours of history. If a full
24-hour point exists, the change is measured from the oldest point inside that window;
otherwise it falls back to the oldest observation. This is a local observation, not a
server-wide 24h statistic.

`volume` is the number of recent sale rows seen in the fetched transaction pages. It is a
sample, not a complete market-wide volume counter.

---

## 3. Valuation

```
investedValue = Σ (holding.qty × trustedPrice)
netWorth      = cash + investedValue
```

`trustedPrice` is `bestPrice` only when the quote is `LIVE` or `MANUAL`. If a position has
no trusted quote, DonutDeck carries it at your recorded average cost rather than inventing
a market price. That keeps net worth continuous, but it means an unquoted position is not
marked to market. Add a manual price if you want it marked.

Realised P/L is updated when you mark a sell intent **FILLED**:

```
realised = (sellPrice - averageCost) × quantitySold
```

The portfolio is only as accurate as your fills and `/bal` syncs. DonutDeck does not read
your inventory, containers, or server-side balance automatically.

---

## 4. Auto-invest advisory

The planner is intentionally simple and transparent.

Inputs:

- `netWorth` from trusted quotes (or cost basis fallback);
- `reserveCash` — cash you do not want to invest;
- `base = max(0, netWorth - reserveCash)` — the investable base;
- target percentages from **Invest → Targets**;
- `bandPct` — how far from target before a ticket is worth creating;
- `minTicket` — ignore tiny rebalances;
- `maxPerOrder` — cap a single ticket;
- `maxTickets` — cap the plan length;
- `allowSells` — whether overweight positions may produce sell tickets.

For each target allocation:

```
desiredValue = base × targetPct / 100
currentValue = currentPct / 100 × base
gap          = desiredValue - currentValue
band         = max(minTicket, base × bandPct / 100)
```

If `gap > band`:

```
amount = min(gap, maxPerOrder, cashAvailable)
qty    = floor(amount / bestPrice)
ticket = BUY qty @ bestPrice
cashAvailable -= qty × bestPrice
```

If `gap < -band` and sells are allowed:

```
qty    = min(heldQty, floor(min(-gap, maxPerOrder) / bestPrice))
ticket = SELL qty @ bestPrice
```

Tickets below `minTicket` are dropped. The remaining tickets are sorted by absolute value
and capped at `maxTickets`.

### Hypothetical example (numbers are illustrative only)

Suppose your investable base is 100,000,000, an item's target is 30%, its current value is
10,000,000, the band is 2,000,000, and the trusted price is 10,000,000:

```
desired = 30,000,000
current = 10,000,000
gap     = 20,000,000
qty     = floor(min(20,000,000, maxPerOrder) / 10,000,000) = 2
```

The planner would show a **BUY 2** ticket. It would not buy anything. You would click
**QUEUE**, review the draft in the Trade desk, click **SEND**, click **CONFIRM**, complete
the server's auction/trade GUI yourself, and then mark the intent **FILLED**.

### Defaults

| Setting | Default | Notes |
|---|---:|---|
| Cash reserve | 50,000,000 | Change this to fit your bankroll. |
| Max per order | 100,000,000 | Caps one ticket. |
| Min ticket | 1,000,000 | Avoids dust rebalances. |
| Rebalance band | 2.0% | Of the investable base. |
| Max tickets | 8 | 1–16. |
| Allow sells | on | Turn off for buys-only. |

These are **risk settings**, not price assumptions. They do not imply that any item is
worth any particular amount.

---

## 5. Buyer tax estimate

The trade composer can show an estimated buyer tax for auction purchases. The percentage
is a local setting (default 2.0%) and is used only for the preview:

```
tax   = total × buyerTaxPct / 100    (BUY + AUCTION only)
total = qty × unitPrice + tax
```

Verify the actual fee in the server's auction GUI. If DonutSMP changes its fee, update the
setting. The estimate does not affect the planner's ticket prices.

---

## 6. Broadcast templates

The Broadcast Center uses templates stored in `state.json`:

```text
templateTrade  = Filled {side} {qty}x {item} @ {price} ({total}) with {counterparty}
templateInvest = AutoInvest plan: {buys} buys / {sells} sells, {tickets} tickets, {total}. Reserve {reserve}.
templateStatus = {message}
```

Available placeholders:

- `{side}`, `{qty}`, `{item}`, `{price}`, `{total}`, `{counterparty}`, `{pnl}`
- `{buys}`, `{sells}`, `{tickets}`, `{reserve}`

The portfolio broadcast includes net worth, cash, invested value, unrealised P/L, position
count, and the largest holdings (three by default, six with **Full portfolio details**).
That is the "broadcast all its stuff" summary. It never includes your API key, webhook
URL, or full state file.

---

## 7. What the mod does not know

- Your real inventory. You mark fills; the portfolio follows your marks.
- Your real cash until `/bal` replies or you mark a fill.
- Other players' private trades, off-platform deals, or future prices.
- Whether an auction listing is a good deal. The planner compares your target weights to
  trusted quotes; it does not predict the market.

Use manual prices for items the API does not cover well. Re-check them regularly. Do not
treat a `MANUAL` quote as live data.
