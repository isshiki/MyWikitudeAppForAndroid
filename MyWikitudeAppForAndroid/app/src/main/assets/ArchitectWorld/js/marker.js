function Marker(poiData) {

	/*
		マーカー（＝ポップアップするバルーンUI）を作成するには、
		ジオロケーション（＝地球上の三次元空間座標）に結び付けられた新しいAR.GeoObjectオブジェクト（＝ロケーションベースのARオブジェクト）を作成します。 
		このAR.GeoObjectは、必ず1つ以上のAR.GeoLocation（＝ロケーション）と、複数の関連付けられたAR.Drawable（＝描画物）が必要です。
		AR.Drawablesは、カメラ（cam）や、レーダー（radar）、方向インジケーター（indicator）といったターゲットに対して定義できます。
	*/

	this.poiData = poiData;

	// POIデータ（緯度＝latitude、経度＝longitude、高度＝altitude）からマーカーロケーション（＝AR.GeoLocationオブジェクト）を作成します。
	var markerLocation = new AR.GeoLocation(poiData.latitude, poiData.longitude, poiData.altitude);

	// アイドル状態時のイメージリソースと、高さ（2.5）、各種オプションを指定して、マーカー用のAR.ImageDrawable（＝画像の描画物）を作成します。
	this.markerDrawable_idle = new AR.ImageDrawable(World.markerDrawable_idle, 2.5, {
		zOrder: 0,
		opacity: 1.0,
		/*
			ユーザーの操作を受け付けるには、それぞれのAR.DrawableでonClickプロパティに関数をセットしてください。 
			この関数は、ユーザーが描画物をタップするたびに呼ばれます。この例では、本ファイル内に定義された下記のヘルパー関数を指定しています。
			クリックされたマーカーは、引数としてこの関数に渡されています。
		*/
		onClick: Marker.prototype.getOnClickTrigger(this)
		// オプションは数が多いので割愛。こちらを参照： http://docs.grapecity.com/help/wikitude/wikitude-sdk-js-api-reference/classes/ImageDrawable.html
	});

	// 選択状態のマーカー用のAR.ImageDrawableを作成します。
	this.markerDrawable_selected = new AR.ImageDrawable(World.markerDrawable_selected, 2.5, {
		zOrder: 0,
		opacity: 0.0,
		onClick: null
	});

	// マーカーの距離表示用のAR.Label（＝ラベルの描画物）を作成します。
	this.distanceLabel = new AR.Label(poiData.distance.trunc(10), 1, {
		zOrder: 1,
		offsetY: 0.55,
		style: {
			textColor: '#FFFFFF',
			fontStyle: AR.CONST.FONT_STYLE.BOLD
		}
		// オプションは数が多いので割愛。こちらを参照： http://docs.grapecity.com/help/wikitude/wikitude-sdk-js-api-reference/classes/Label.html
	});

	// マーカーのレストラン名表示用のAR.Labelを作成します。
	this.nameLabel = new AR.Label(poiData.name.trunc(15), 0.8, {
		zOrder: 1,
		offsetY: -0.55,
		style: {
			textColor: '#FFFFFF'
		}
	});

	// 1つ以上のマーカーロケーションと、複数の描画物を指定して、AR.GeoObjectを作成します。
	this.markerObject = new AR.GeoObject(markerLocation, {
		drawables: {
			cam: [this.markerDrawable_idle, this.markerDrawable_selected, this.distanceLabel, this.nameLabel]
		}
		/*
			ここではdrawablesオプションを指定していますが、指定可能なオプションは以下のとおりです。
			 ・enabled： Boolean型（デフォルト値: true）。有効／無効を指定します。
			 ・renderingOrder： Number型（デフォルト値: 0） 。描画順序を指定します。
			 ・onEnterFieldOfVision：AR.GeoObjectが表示開始された時の処理を実施する関数を指定します。
			 ・onExitFieldOfVision： AR.GeoObjectが表示終了する時の処理を実施する関数を指定します。
			 ・onClick： ユーザークリックを処理する関数を指定します。
			 ・drawables.cam： Drawable[]型。カメラビュー内の描画物を指定します。
			 ・drawables.radar： Drawable2D[]型。レーダー内の描画物を指定します。
			 ・drawables.indicator： Drawable2D[]型。方向インジケーター内の描画物を指定します。
		*/
	});

	return this;
}

Marker.prototype.getOnClickTrigger = function(marker) {

	/*
		この関数内では、描画物の選択状態を判定して、選択状態を設定し直し、適切な処理が実行されます。
	*/
	return function() {
		
		if (marker.isSelected) {
			
			Marker.prototype.setDeselected(marker);
			
		} else {
			Marker.prototype.setSelected(marker);
			try {
				World.onMarkerSelected(marker);
			} catch (err) {
				alert(err);
			}
			
		}
		
		return true;
	};
};

Marker.prototype.setSelected = function(marker) {
	
	marker.isSelected = true;
	
	marker.markerDrawable_idle.opacity = 0.0;
	marker.markerDrawable_selected.opacity = 1.0;
	
	marker.markerDrawable_idle.onClick = null;
	marker.markerDrawable_selected.onClick = Marker.prototype.getOnClickTrigger(marker);
};

Marker.prototype.setDeselected = function(marker) {
	
	marker.isSelected = false;
	
	marker.markerDrawable_idle.opacity = 1.0;
	marker.markerDrawable_selected.opacity = 0.0;
	
	marker.markerDrawable_idle.onClick = Marker.prototype.getOnClickTrigger(marker);
	marker.markerDrawable_selected.onClick = null;
};

// Stringクラスに長すぎる文字列を短く省略するための関数を追加定義します。
String.prototype.trunc = function(n) {
	return this.substr(0, n - 1) + (this.length > n ? '...' : '');
};