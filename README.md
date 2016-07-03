# MyWikitudeAppForAndroid

Wikitude SDK（JavaScript API）を用いたロケーションベースARのサンプルアプリで、周囲500m範囲内（最大10件）のレストランの方角と距離をポップアップ表示します。

連載記事「[Wikitudeで「オープンデータ＋AR（拡張現実）」スマホアプリをお手軽開発［PR］ - Build Insider](http://www.buildinsider.net/pr/grapecity/wikitude)」の第3回で作成したAndroid向けのサンプルアプリです。

このサンプルアプリは、SDKに付属のサンプルをベースに独自の拡張を加えたものです。

## ビルド・実行するための注意点

- /MyWikitudeAppForAndroid/app/src/main/java/isshiki/mywtappforandroid/MainActivity.java

MainActivity.javaファイル内の`WIKITUDE_SDK_KEY`の値を、実際に取得した正しい「Wikitude」のキーにしてください。

- /MyWikitudeAppForAndroid/app/src/main/assets/ArchitectWorld/js/mainlogic.js

mainlogic.jsファイル内の`webApiKeyID`の値を、実際に取得した正しい「リクルートWEBサービス（ホットペーパー）」のキーにしてください。


## ライセンス

MIT.



