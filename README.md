# BowRounds

ROUNDS 風のローグライク弓PvP を Minecraft Paper プラグインとして作るプロジェクトです。
ラウンドごとに勝者以外の全員が強化カードを1枚選び、ビルドを積み上げながら戦います。大人数での対戦を前提にしています。

- 設計: [docs/game-design.md](docs/game-design.md)
- 対象: Paper 26.2 / Java 25

## ダウンロード

[BowRounds.jar（最新版）](https://github.com/savamiso2/bow_pvp/releases/latest/download/BowRounds.jar) をクリックするとダウンロードできます。Paper サーバーの `plugins` フォルダに入れて、サーバーを再起動してください。

## ビルド

```
gradle build
```

`build/libs/` に jar ができます。GitHub Actions でも PR ごとにビルドし、jar を成果物として置きます。
`gradle.properties` の `paperVersion` / `apiVersion` は、実際に公開されている paper-api のバージョンに合わせてください。

## 遊び方（MVP）

アリーナは専用ワールドに作るのがおすすめです（ラウンド中はそのワールドのワールドボーダーを使います）。

1. `/br setlobby` … 試合後に戻る場所
2. `/br arena create <名前> <最大人数>` … 今いる場所がアリーナの中心
3. `/br arena addspawn <名前>` … スポーン地点を追加（人数分くらい）
4. `/br arena border <名前> <サイズ>` / `/br arena players <名前> <最小> <最大>`
5. 参加者が `/br join`、管理者が `/br start`

| コマンド | 内容 |
|---|---|
| `/br join` / `/br leave` | 参加・離脱 |
| `/br status` | 参加人数と試合状況 |
| `/br start` / `/br stop` | 試合開始・停止（OP） |
| `/br arena list` / `delete <名前>` | アリーナ一覧・削除（OP） |

数値（体力・装填数・ポイント配分など）は `config.yml` で変更できます。

## 操作

- 右クリック長押し：弓を引く（装填数を撃ち切ると自動リロード）
- 左クリック：ガード（0.3秒だけ矢を防ぐ。クールダウン4秒、XPバーが満タンなら使える）。衝撃波・ブリンク・ダッシュなどのカード能力もガードと同時に発動

## カード選択

ラウンドが終わると、勝者以外の目の前にカードが浮かび上がります（自分のカードは自分にしか見えません）。見ているカードが光るので、クリックで決定します。`config.yml` の `cards.style: menu` にすると、チェスト型の画面で選ぶ方式に戻せます。

## 収録カード（20枚）

散弾 / 跳弾 / 速装填 / 大容量 / 頑丈 / 毒矢 / 重力無視 / 追尾 / 吸血 / ジェット / 剛弓 / 爆裂矢 / 貫通 / 分裂 / ノックバック

ガード強化：反射 / 衝撃波 / ガード上手 / ブリンクガード / ダッシュ
