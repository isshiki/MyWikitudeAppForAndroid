// ARchitect World（＝AR体験）の実装
var World = {
	
	// TODO: 適切な「リクルートWEBサービス（ホットペッパー）」のキーを指定してください。
	webApiKeyID: '＜ライセンスキーを書き直してください！ 例：abc01234567890de＞',

	// データロードを1回のみにするためのフラグ。
	initiallyLoadedData: false,

	// ロケーション情報を変更中かを判定するフラグ。
	changingLocationDisplay: false,

	// さまざまなPOIのMarkerのアセット（assets）。
	markerDrawable_idle: null,
	markerDrawable_selected: null,

	// ARchitect World内に表示されているAR.GeoObjectリスト。
	markerList: [],

	// 最後に選択されたマーカー。
	currentMarker: null,

	// POIマーカーを作成・表示するために呼ばれる関数。
	displayPOIs: function (poiData) {
		
		AR.context.destroyAll();
		
		// マーカーのアセットをイメージリソースとしてロードします。
		World.markerDrawable_idle = new AR.ImageResource("assets/marker_idle.png");
		World.markerDrawable_selected = new AR.ImageResource("assets/marker_selected.png");
		
		// 見えるマーカーのリストを空にします。
		World.markerList = [];
		
		// 全てのPOI情報をループしながら、1つのPOIごとにAR.GeoObject（＝マーカー）を作成します。近場の10件を、遠いとこから順に作成していきます。
		if (poiData.length <= 0) return;
		var renderingMaxCount = 10;
		for (var number = (poiData.length < renderingMaxCount) ? (poiData.length - 1) : (renderingMaxCount - 1); number >= 0; number--) {
			
			var singlePoi = {
				"id":          poiData[number].id,
				"name":        poiData[number].name,
				"latitude":    poiData[number].latitude,
				"longitude":   poiData[number].longitude,
				"altitude":    poiData[number].altitude,
				"distance":    poiData[number].distance
			};
			
			// ユーザーが何もないスクリーン上をタップした際に選択中のマーカーを選択解除できるようにするため、
			// 1つ1つのマーカーが含まれる配列をWorldオブジェクトに保持させます。
			World.markerList.push(new Marker(singlePoi, renderingMaxCount - number));
		}
		
	},

	// 下部中央付近にあるフッターに、警告時＝［△］／通常時［i］の小さなボタンを表示し、そのボタンクリック時にポップアップする状態メッセージを更新します。
	updateStatusMessage: function (message, isWarning) {
		
		var themeToUse = isWarning ? "e" : "c";
		var iconToUse = isWarning ? "alert" : "info";
		
		$("#status-message").html(message);
		$("#popupInfoButton").buttonMarkup({
			theme: themeToUse
		});
		$("#popupInfoButton").buttonMarkup({
			icon: iconToUse
		});
	},

	// ロケーションを更新します。Androidネイティブ環境でarchitectView.setLocationメソッドが呼び出されるたびに、この関数は呼び出されます。iOSではネイティブサービスのstartUpdatingLocationメソッドを呼び出すとここが呼び出されるようです（※ドキュメントなし）。
	locationChanged: function (lat, lon, alt, acc) {
		
		if (World.changingLocationDisplay) return;
		World.changingLocationDisplay = true;
		
		// World.initiallyLoadedDataフラグを確認して、初回起動時にのみPOIデータをロードする処理を実行します。
		if (!World.initiallyLoadedData) {
			// 緯度・経度などを指定してrequestPOIsFromWebAPI関数を呼び出し、現在地周辺のPOIデータを取得し、マーカーを作成します。
			World.requestPOIsFromWebAPI(lat, lon, alt, acc);
			World.initiallyLoadedData = true;  // 最後にフラグを「読み込み済み」（＝true）に設定します。
			
		} else {
			// 緯度・経度などを指定してupdatePOIs関数を呼び出し、現在地周辺のPOIデータを更新し、マーカーを作成します。
			World.updatePOIs(lat, lon, alt, acc);
		}
		
		// テスト表示用（［i］ボタンをタップすると表示される状態メッセージをセットします）。
		World.updateStatusMessage(World.markerList.length + "件、緯度・経度・高度・精度：" + lat + ", " + lon + ", " + alt + ", " + acc);
		//alert("緯度・経度：" + lat + ", " + lon);
		
		World.changingLocationDisplay = false;
	},

	// カメラビュー内でユーザーがマーカーを押した時に呼び出されます。
	onMarkerSelected: function (marker) {
		
		// 前回選択されていたマーカーの選択を解除します。
		if (World.currentMarker) {
			if (World.currentMarker.poiData.id == marker.poiData.id) {
				return;
			}
			World.currentMarker.setDeselected(World.currentMarker);
		}
		
		// 今回のマーカーを選択してハイライトします。
		marker.setSelected(marker);
		World.currentMarker = marker;
	},

	// ロケーションARオブジェクト以外のスクリーンがクリッックされた時に呼び出されます。
	onScreenClick: function () {
		if (World.currentMarker) {
			World.currentMarker.setDeselected(World.currentMarker);
		}
	},

	// 指定された地点における全てのPOIデータをロードします。
	requestPOIsFromWebAPI: function (centerPointLatitude, centerPointLongitude, centerPointAltitude, centerPointAccuracy) {
		
		// 念のためValidate（値検証）しています。
		if ("number" !== typeof centerPointLatitude || centerPointLatitude < -90) centerPointLatitude = -90;
		if ("number" !== typeof centerPointLatitude || centerPointLatitude > 90) centerPointLatitude = 90;
		if ("number" !== typeof centerPointLongitude || centerPointLongitude < -180) centerPointLongitude = -180;
		if ("number" !== typeof centerPointLongitude || centerPointLongitude > 180) centerPointLongitude = 180;
		//if ("number" !== typeof centerPointAltitude) centerPointAltitude = parseFloat(centerPointAltitude); // 「centerPointAltitude = AR.CONST.UNKNOWN_ALTITUDE;」としても、同じユーザーレベル高度になります。本サンプルでは常に「0.0」が渡されています。
		if ("number" !== typeof centerPointAccuracy || centerPointAccuracy > 20) return; // 精度値が20mより大きい場合は精度が悪すぎるので、以下の処理を実行しない。
		
		// cachedDataオブジェクトは、cacheddata.jsに固定的に定義しておいたレストランのPOIデータです。
		// 本サンプルでは、ここで「ホットペッパー」のWeb APIから動的にレストランデータを取得しています。
		// Web APIを使わないで動作をテストしたい場合は、以下のコメントアウトを解除して、その下ののWebAPIの処理をコメントアウトてしてください。
		//World.loadPOIsFromCachedData(centerPointLatitude, centerPointLongitude, centerPointAltitude, centerPointAccuracy);
		
		// 以下では、「ホットペッパー」のWeb APIから動的にレストランデータを取得して、それをPOIデータに加工して表示しています。
		cachedData = [];  // 新しく検索しなおすので、全てのデータをクリアしています。
		var params = {
			keyid: World.webApiKeyID,
			format: 'jsonp',
			callback: '&callback=?',           // jQueryにより「?」にはコールバック関数名が自動セットされます。
			latitude: centerPointLatitude,
			longitude: centerPointLongitude,
			range: 3,                         // 中心点からの検索範囲（半径）。1:300m、2:500m、3:1000m（初期値）、4:2000m、5:3000m
			hit_per_page: 100,                // 1回のMAXは100件。
			offset_page: 1                    // 1ページ目から取得していきます。
		};
		var loadPOIsFromWebApi = function() {
			var url = 'http://webservice.recruit.co.jp/hotpepper/gourmet/v1/?' + 
				'key=' + params.keyid + 
				'&lat=' + params.latitude + 
				'&lng=' + params.longitude + 
				'&range=' + params.range +
				'&count=' + params.hit_per_page +
				'&start=' + ((params.hit_per_page * (params.offset_page - 1)) + 1) +
				'&format=' + params.format + 
				params.callback;
			$.getJSON(url)
			.done(function(json) {
				var total_hit_count = parseInt(json.results.results_returned);
				if (total_hit_count > 0) {
					for (var n in json.results.shop) {
						cachedData.push({
							"id":        (json.results.shop[n].id),
							"name":      (json.results.shop[n].name),
							"latitude":  parseFloat(json.results.shop[n].lat),
							"longitude": parseFloat(json.results.shop[n].lng),
						});  // 再ロードをできるだけ抑折するために、少し広めのレストランJSONデータをcachedData変数に保持しておきます。
					}
					if ((params.hit_per_page * params.offset_page) < total_hit_count) {
						params.offset_page++;
						loadPOIsFromWebApi();  // さらに次のページのデータを読み込む（再帰呼び出し）
					} else {
						World.loadPOIsFromCachedData(centerPointLatitude, centerPointLongitude, centerPointAltitude, centerPointAccuracy, false);  // これ以上、データは読み込まない。
					}
				} else {
					if (params.offset_page == 1) {
						World.updateStatusMessage("0件（レストランが見付かりません！）、緯度・経度：" + centerPointLatitude + ", " + centerPointLongitude);
					} else {
						World.loadPOIsFromCachedData(centerPointLatitude, centerPointLongitude, centerPointAltitude, centerPointAccuracy, false);  // これ以上、データは読み込まない。
					}
				}
			});
		};
		loadPOIsFromWebApi();
	},

	// cachedData変数の値をフィルタリングや変換を掛けながらPOIデータに移し替え、それを使ってPOIマーカーをロードします。
	loadPOIsFromCachedData: function(centerPointLatitude, centerPointLongitude, centerPointAltitude, centerPointAccuracy, doSort) {
		
		var poiData = [];
		
		for (var i = 0, length = cachedData.length; i < length; i++) {
			
			var distance = World.getDistance(cachedData[i].latitude, centerPointLatitude, cachedData[i].longitude, centerPointLongitude);
			if (distance > 500.0) continue;  // 0.5km（＝500m）以上先のPOIデータは破棄します。
			var distanceString = (distance > 999) ? ((distance / 1000).toFixed(2) + " km") : (Math.round(distance) + " m");
			
			poiData.push({
				"id":        (cachedData[i].id),
				"name":      (cachedData[i].name),       // レストラン名。
				"latitude":  (cachedData[i].latitude),   // 緯度。
				"longitude": (cachedData[i].longitude),  // 経度。
				"altitude":  (0.0),                      // 高度。ちなみに標高の平均といえる「日本水準原点」の値は「24.3900」です。
				"distance":  (distanceString),           // 現在の地点からの距離（単位は「km」もしくは「m」）。
				"sortorder": (distance)                  // 距離でソートできるようにしています。
			});
		}
		
		// キャッシュデータの場合のみ、距離が近い順でソートします。ホットペッパーAPIでは位置検索の場合は距離順で返してくれるので、ソートはしない。
		if (doSort) poiData.sort(function(a,b){return a.sortorder - b.sortorder});
		
		// 距離がソートされた状態なので、距離と順番を基準にして高度を設定することで、マーカーの位置をばらけさせます。
		for (var n = 0, length = poiData.length; n < length; n++) {
			var altitudeToSpread = n * Math.sqrt(distance / 3) - 1.5;  // 本サンプルでは基準「-1.5」から上に向けてPOIマーカーの位置をバラけさせて表示しています。遠いほど倍々で高度が高くなります。「-1.5m」は手持ちのスマホの高さを考慮しています
			poiData[n].altitude = altitudeToSpread;
			//AR.logger.info(poiData[n].name + "［距離］：" + poiData[n].distance + "｜［高度］：" + poiData[n].altitude); // 距離と高度を確認するためのデバッグ用コード
		}
		
		World.displayPOIs(poiData);
	},

	// 表示されているARオブジェクトの距離表示を更新します。
	updatePOIs: function (centerPointLatitude, centerPointLongitude, centerPointAltitude, centerPointAccuracy) {
		
		var isChangeOrder = false;
		var lastDistance = -1;
		for (var n = 0; n < World.markerList.length; n++) {
			
			var distance = World.getDistance(World.markerList[n].poiData.latitude, centerPointLatitude, World.markerList[n].poiData.longitude, centerPointLongitude);
			//AR.logger.info(World.markerList[n].poiData.name + "【" + Math.round(distance) + "m】" + centerPointLatitude + "|" + centerPointLongitude + "|" + centerPointAltitude + "|" + centerPointAccuracy); // 位置情報の更新を確認するためのデバッグ用コード（ラベル更新しない場合を含む）
			if (distance < lastDistance) isChangeOrder = true;
			lastDistance = distance;
			
			if (distance > 500.0) {
				// 既存のマーカーの中から0.5km（＝500m）以上先のPOIデータが出てきた場合は、全部をリロードし直します。
				World.requestPOIsFromWebAPI(centerPointLatitude, centerPointLongitude, centerPointAltitude, centerPointAccuracy);
				return;
			}
			
			var distanceString = (distance > 999) ? ((distance / 1000).toFixed(2) + " km") : (Math.round(distance) + " m");
			//AR.logger.info(World.markerList[n].poiData.name + "［ラベル更新］：" + World.markerList[n].poiData.distance + " => " + distanceString); // 位置情報更新とラベル表記変更を確認するためのデバッグ用コード
			World.markerList[n].distanceLabel.text = World.markerList[n].poiData.distance = distanceString;  // ラベルとデータの両方を変更
		}
		if (isChangeOrder == false) return;
		
		World.loadPOIsFromCachedData(centerPointLatitude, centerPointLongitude, centerPointAltitude, centerPointAccuracy, true);
	},

	getDistance: function (targetLatitude, centerPointLatitude, targetLongtitude, centerPointLongitude) {
		// 参考：http://www.movable-type.co.uk/scripts/latlong.html
		var Δφ = (centerPointLatitude - targetLatitude) * Math.PI / 180;
		var Δλ = (centerPointLongitude - targetLongtitude) * Math.PI / 180;
		var a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) + Math.cos(targetLatitude * Math.PI / 180) * Math.cos(centerPointLatitude * Math.PI / 180) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
		var c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return 6371e3 * c;
	},

};

/*
	ロケーションが変更された時の処理を実行する関数をセットします。
*/
AR.context.onLocationChanged = World.locationChanged;

/*
	（描画物をよけて）スクリーンがクリックされた時の処理を実行する関数をセットします。選択中のマーカーを非選択状態にするなどの処理が考えられます。
*/
AR.context.onScreenClick = World.onScreenClick;
