// ARchitect World（＝AR体験）の実装
var World = {
	
	// TODO: 適切な「リクルートWEBサービス（ホットペーパー）」のキーを指定してください。
	webApiKeyID: '＜ライセンスキーを書き直してください！＞00a',

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

	// 新しいPOIデータを注入するために呼ばれる関数。
	loadPoisFromJsonData: function (poiData) {
		
		AR.context.destroyAll();
		
		// マーカーのアセットをイメージリソースとしてロードします。
		World.markerDrawable_idle = new AR.ImageResource("assets/marker_idle.png");
		World.markerDrawable_selected = new AR.ImageResource("assets/marker_selected.png");
		
		// 見えるマーカーのリストを空にします。
		World.markerList = [];
		
		// 全てのPOI情報をループしながら、1つのPOIごとにAR.GeoObject（＝マーカー）を作成します。近場の10件を、遠いとこから順に作成していきます。
		if (poiData.length <= 0) return;
		for (var currentPlaceNr = (poiData.length < 10) ? (poiData.length - 1) : (10 - 1); currentPlaceNr >= 0; currentPlaceNr--) {
			
			var singlePoi = {
				"id":          poiData[currentPlaceNr].id,
				"name":        poiData[currentPlaceNr].name,
				"latitude":    poiData[currentPlaceNr].latitude,
				"longitude":   poiData[currentPlaceNr].longitude,
				"altitude":    poiData[currentPlaceNr].altitude,
				"distance":    poiData[currentPlaceNr].distance
			};
			
			// ユーザーが何もないスクリーン上をタップした際に選択中のマーカーを選択解除できるようにするため、
			// 1つ1つのマーカーが含まれる配列をWorldオブジェクトに保持させます。
			World.markerList.push(new Marker(singlePoi));
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
			// 渡された緯度（＝latitude）と経度（＝longitude）を指定して、requestDataFromWebAPI関数を呼び出し、現在地周辺のPOIデータを取得します。
			// 最後にフラグを「読み込み済み」（＝true）に設定します。
			World.requestDataFromWebAPI(lat, lon, alt, acc);
			World.initiallyLoadedData = true;
			
		} else {
			// 対象地点からの距離情報を頻繁に更新します。
			World.updateDistanceValues(lat, lon, alt, acc);
		}
		
		// テスト表示用（［i］ボタンをタップすると表示される状態メッセージをセットします）。
		World.updateStatusMessage(World.markerList.length + "件、緯度・経度：" + lat + ", " + lon);
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
	requestDataFromWebAPI: function (centerPointLatitude, centerPointLongitude, centerPointAltitude, centerPointAccuracy) {
		
		// 念のためValidate（値検証）しています。
		if ("number" !== typeof centerPointLatitude || centerPointLatitude < -90) centerPointLatitude = -90;
		if ("number" !== typeof centerPointLatitude || centerPointLatitude > 90) centerPointLatitude = 90;
		if ("number" !== typeof centerPointLongitude || centerPointLongitude < -180) centerPointLongitude = -180;
		if ("number" !== typeof centerPointLongitude || centerPointLongitude > 180) centerPointLongitude = 180;
		if ("number" !== typeof centerPointAltitude) centerPointAltitude = parseFloat(centerPointAltitude); // 「centerPointAltitude = AR.CONST.UNKNOWN_ALTITUDE;」としても、ユーザーレベル高度になります。
		if (centerPointAccuracy > 20) return; // 精度値が20mより大きい場合は精度が悪すぎるので、以下の処理を実行しない
		
		var poiData = [];
		
		var loadPoisFromMyJsonDataVariable = function() {
		
			for (var i = 0, length = myJsonData.length; i < length; i++) {
				
				var distance = World.getDistance(myJsonData[i].latitude, centerPointLatitude, myJsonData[i].longitude, centerPointLongitude);
				if (distance > 500.0) continue;  // 0.05km（＝500m）以上先のPOIデータは破棄します。
				var distanceString = (distance > 999) ? ((distance / 1000).toFixed(2) + " km") : (Math.round(distance) + " m");
				
				poiData.push({
					"id":        (myJsonData[i].id),
					"name":      (myJsonData[i].name),       // レストラン名。
					"latitude":  (myJsonData[i].latitude),   // 緯度。
					"longitude": (myJsonData[i].longitude),  // 経度。
					"altitude":  (centerPointAltitude),      // 高度。現在地の高度に合わせて表示しています。ちなみに標高の平均といえる「日本水準原点」の値は「24.3900」です。
					"distance":  (distanceString),           // 現在の地点からの距離（単位は「km」もしくは「m」）。
					"sortorder": (distance)                  // 距離でソートできるようにしています。
				});
			}
			//poiData.sort(function(a,b){return a.sortorder - b.sortorder}); // 距離が近い順でソートする（※ホットペッパーAPIでは位置検索の場合は距離順で返してくれる）。
			
			World.loadPoisFromJsonData(poiData);
		};
		
		// myJsonDataオブジェクトは、myjsondata.jsに固定的に定義しておいたレストランのPOIデータです。
		// 本サンプルでは、ここで「ぐるなび」のWeb APIから動的にレストランデータを取得しています。
		// Web APIを使わないで動作をテストしたい場合は、以下のコメントアウトを解除して、その下ののWebAPIの処理をコメントアウトてしてください。
		//loadPoisFromMyJsonDataVariable();
		
		// 以下では、「ぐるなび」のWeb APIから動的にレストランデータを取得して、それをPOIデータに加工して表示しています。
		myJsonData = [];  // 新しく検索しなおすので、全てのデータをクリアしています。
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
		var loadPoisFromWebApi = function() {
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
						myJsonData.push({
							"id":        (json.results.shop[n].id),
							"name":      (json.results.shop[n].name),
							"latitude":  parseFloat(json.results.shop[n].lat),
							"longitude": parseFloat(json.results.shop[n].lng),
						});  // 再ロードをできるだけ抑折するために、少し広めのレストランJSONデータをmyJsonData変数に保持しておきます。
					}
					if ((params.hit_per_page * params.offset_page) < total_hit_count) {
						params.offset_page++;
						loadPoisFromWebApi();       // さらに次のページのデータを読み込む（再帰呼び出し）
					} else {
						loadPoisFromMyJsonDataVariable(); // これ以上、データは読み込まない。
					}
				} else {
					if (params.offset_page == 1) {
						World.updateStatusMessage("0件（レストランが見付かりません！）、緯度・経度：" + lat + ", " + lon);
					} else {
						loadPoisFromMyJsonDataVariable(); // これ以上、データは読み込まない。
					}
				}
			});
		};
		loadPoisFromWebApi();
	},
	
	// 表示されているARオブジェクトの距離表示を更新します。
	updateDistanceValues: function (centerPointLatitude, centerPointLongitude, centerPointAltitude, centerPointAccuracy) {
		
		for (var i = 0; i < World.markerList.length; i++) {
			
			var distance = World.getDistance(World.markerList[i].poiData.latitude, centerPointLatitude, World.markerList[i].poiData.longitude, centerPointLongitude);
			//AR.logger.info(World.markerList[i].poiData.name + "【" + Math.round(distance) + "m】" + centerPointLatitude + "|" + centerPointLongitude + "|" + centerPointAltitude + "|" + centerPointAccuracy); // 位置情報の更新を確認するためのデバッグ用コード（ラベル更新しない場合を含む）
			if (distance > 500.0) {
				// 既存のマーカーの中から0.5km（＝500m）以上先のPOIデータが出てきた場合は、全部をリロードし直します。
				World.requestDataFromWebAPI(centerPointLatitude, centerPointLongitude, centerPointAltitude, centerPointAccuracy);
				return;
			}
			var distanceString = (distance > 999) ? ((distance / 1000).toFixed(2) + " km") : (Math.round(distance) + " m");
			//AR.logger.info(World.markerList[i].poiData.name + "［ラベル更新］：" + World.markerList[i].poiData.distance + " => " + distanceString); // 位置情報更新とラベル表記変更を確認するためのデバッグ用コード
			World.markerList[i].distanceLabel.text = World.markerList[i].poiData.distance = distanceString;  // ラベルとデータの両方を変更
		}
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
