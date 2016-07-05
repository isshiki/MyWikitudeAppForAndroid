package isshiki.mywikitudeappforandroid;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import com.wikitude.architect.ArchitectView;
import com.wikitude.architect.StartupConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * ARchitectView（＝ARのビュー）を全面表示するメインのアクティビティ。
 */
public class MainActivity extends AppCompatActivity {

	// region 各種定数

	/** Wikitudeのライセンスキーを定数として定義。*/
	// TODO: 実際に取得した正しいWikitudeのアクセスキーに変更してください。
	protected static final String WIKITUDE_SDK_KEY = @"＜ライセンスキーを書き直してください！＞/o08s01R0N0vP+0fqFtL1P2yIlpuzsQ0knsi++V0JQ0pfng86gDjUW0DHzRGG1/LW0gv0+0H1JtkT6VfvqJs+J0462pDs646PrGNGrL0ZW0YOWfZ1hz0Si0QJTYW00ZWRf0y0+LSZ60DUUrsjGp00/l0SYNLvKGJ0tHH00iRS0O062w00H6Hr0qMo0kH+4202tz0H002LSVRZ01SpZTwPN04GiZJQKZg0zRrTHF0HqNMss8ofi02WoPUQ80FyWh0p20zOGPd/0souP4YT010T0SG0g/pOndNdZSYRj0H0ph040uYTZYDYgmmM0kIpQWd0gFUTHt8ZnsWniyssMm2yS8T60JPl0V0G000ZJ0f0KSZJtDMMoyU0Fq0U00T00m0M//800inl00V0/0izPFHU000vPIo40n0UhHoJ/OO080tsuQt0SwsrDIm08Tfv8FnM00i0zQF6d4kQ0FPh0vVK6WzG0Y0M0y006n00zno+00PTRSK/oDs/Jz0+8qNzzGT000zhlq0jYrP/S0G0LLy0VopOzhnSLv4NKKrL1IZRr01NtMro0FPgUj1000k0lYG0l0tGFisrpMqFU1hFMrQSr0100qL/znlrPzk0V6n00qmyoR0UnOgkK80w=";

	/** アクティビティのタイトルを定数として定義。*/
	protected static final String MAIN_ACTIVITY_TITLE = "Wikitude Android Sample";

	/** ARchitect WorldとなるHTMLファイルのURLを定数として定義。*/
	protected static final String MAIN_ARCHITECT_WORLD_URL = "ArchitectWorld/index.html";

	/** ARchitectViewに指定するARオブジェクトの表示除外基準の距離。デフォルトでは50km。※デフォルトのままなのでコメントアウトしています。*/
	//protected static final int CULLING_DISTANCE_METERS = 50 * 1000;

	/** 高度情報が不明もしくは取得できない場合の値。-32768は地球上には表示できない高度レベルとなります。Wikitude JavaScript APIにおける「AR.CONST.UNKNOWN_ALTITUDE」と同じ値です。*/
	protected static final float UNKNOWN_ALTITUDE = -32768f;


	//endregion

	// region 各種フィールドメンバー

	/** Wikitude SDKのメインコンポーネント「ARchitectView」。このビューに、カメラ、マーカー（＝ポップアップするバルーンUI）、レーダーUI要素、3Dモデルなどがレンダリングされます。*/
	protected ArchitectView architectView;

	/** 端末のセンサーにおける精度が変化したことを受信するためのリスナー。*/
	protected ArchitectView.SensorAccuracyChangeListener sensorAccuracyListener;
	/** 位置情報の更新を受信するためのリスナー。これを、ARchitectViewに通知して、ARchitect Worldの位置情報を更新します。*/
	protected LocationListener locationListener;
	/** JavaScript内のdocument.locationで用いるARchitect World用「architectsdk://」プロトコル経由のURL呼び出しを受信するためのリスナー（※本サンプルではコードはありますが実質的には使っていません）。*/
	protected ArchitectView.ArchitectUrlListener urlListener;

	/** 最も基本的なLocation戦略のサンプル実装（※「 http://goo.gl/pvkXV 」を参照）。LocationProvider.javaファイルのコードを自由にカスタマイズして処理を洗煉させてください。*/
	protected ILocationProvider locationProvider;

	/** 最新のユーザー位置情報。本サンプルでは位置情報が取得されているかどうかの判定で使われています（※本サンプルではコードはありますが実質的には使っていません）。*/
	protected Location lastKnownLocaton;
	/** POI（Point of Interest： 対象地点）のデータ（※本サンプルではコードはありますが実質的には使っていません）。*/
	protected JSONArray poiData;
	/** POIデータをロード中かどうか示すフラグ。データの初期ロードを行うinjectDataメソッド内で使われています（※本サンプルではコードはありますが実質的には使っていません）。*/
	protected boolean isLoading = false;

	/** キャリブレーショントーストが最後に表示された時間。コンパスがキャリブレーションを必要とするときに、トーストが出過ぎるのを抑止するために、時間を管理しています。*/
	private long lastCalibrationToastShownTimeMillis = System.currentTimeMillis();

	/** スクリーンキャプチャした画像（※本サンプルではコードはありますが実質的には使っていません）。*/
	protected Bitmap screenCapture = null;
	/** スクリーンキャプチャ画像を保存する先のストレージへのアクセス権の要求コード（※独自）を定数として定義（※本サンプルではコードはありますが実質的には使っていません）。*/
	private static final int WIKITUDE_PERMISSIONS_REQUEST_EXTERNAL_STORAGE = 3;

	/** ※注意： 以下は、App Indexing API（＝アプリをGoogle検索に登録してもらうためのAPI）を実装するために、Android Studioにより自動生成されたコードです。詳細はこちらを参照（英語）： https://g.co/AppIndexing/AndroidStudio */
	private GoogleApiClient client;

	//endregion

	// region アクティビティのライフサイクル

	/**
	 * ［アクティビティのライフサイクル］アクティビティが作成される時に呼び出されます。
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// カメラ／位置情報／ストレージに対する「アプリの権限」を確認し、なければプロンプトを出す。
		RequestPermission();
		if (this.isFinishing()) return;
		
		// ARビュー用のレイアウト（"\app\src\main\res\layout\activity_main.xml"）を設定します。
		setContentView(R.layout.activity_main);
		
		// アクティビティのタイトルを設定します。
		this.setTitle(MAIN_ACTIVITY_TITLE);
		
		// ChromeのDevToolsを使ってリモートでWebViewsをデバッグできるように構成します。
		// 参考: https://developers.google.com/chrome-developer-tools/docs/remote-debugging
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			WebView.setWebContentsDebuggingEnabled(true);
		}
		
		// ライフサイクル通知などに対するARchitectViewを設定します。
		this.architectView = (ArchitectView) this.findViewById(R.id.architectView);
		
		// SDKキーと必要な機能（ジオロケーションと2D画像トラッキング）を指定します。
		final StartupConfiguration config = new StartupConfiguration(
				WIKITUDE_SDK_KEY,
				StartupConfiguration.Features.Geo | StartupConfiguration.Features.Tracking2D,
				StartupConfiguration.CameraPosition.DEFAULT);
		
		// ARchitectViewのライフサイクルメソッド「onCreate」を呼び出す必要があります。
		try {
			this.architectView.onCreate(config);
		} catch (RuntimeException rex) {
			this.architectView = null;
			Toast.makeText(getApplicationContext(), "can't create Architect View", Toast.LENGTH_SHORT).show();
			Log.e(this.getClass().getName(), "Exception in ArchitectView.onCreate()", rex);
		}
		
		// 精度リスナーのオブジェクトを作成します。このリスナーを活用して、コンパスのキャリブレーションを促すプロンプトなどを表示します。
		this.sensorAccuracyListener = this.getSensorAccuracyListener();
		// ※ARchitectViewへの精度リスナーの登録／登録解除は、ここではなく後述のonResume()メソッド／onPause()メソッドで行っています。
		
		// ARchitect World用URLリスナーのオブジェクトを作成します。「document.location = 'architectsdk://foo?bar=123'」のようにしてJavaScript上で呼び出されたURLはこのリスナーに通知されます。これを利用することで、 「JavaScriptコード」と「ネイティブAndroidアクティビティ／フラグメント」の間でのやり取りが実現できます。
		this.urlListener = this.getUrlListener();
		// ※ARchitectViewへのARchitect World用URLリスナーの登録を行います。イベントの受け取り漏れを無くすために、コンテンツがロードされる前のここで必ず登録するようにしてください。
		if (this.urlListener != null && this.architectView != null) {
			this.architectView.registerUrlListener(this.getUrlListener());
		}
		
		//  位置情報のリスナーを登録します。全ての位置情報更新はここで処理され、ここから本アプリ内で一元的に位置情報を管理するプロバイダー「locationProvider」に引き渡されます。
		this.locationListener = new LocationListener() {
			
			@Override
			public void onStatusChanged(String provider, int status, Bundle extras) {
			}
			
			@Override
			public void onProviderEnabled(String provider) {
			}
			
			@Override
			public void onProviderDisabled(String provider) {
			}
			
			@Override
			public void onLocationChanged(final Location location) {
				// LocationProviderが、位置情報の更新をこのメソッドに伝達します。ここではARchitectViewに位置情報を引き渡すことで、ARchitect Worldの位置情報を更新します。
				if (location != null) {
					// アプリ内のどこかで位置情報が必要になる場合に備えて、最新（＝前回）の位置情報を保存しておきます。
					MainActivity.this.lastKnownLocaton = location;
					if (MainActivity.this.architectView != null) {
						
						// 位置情報を変更通知をテストするためのデバッグ用コード
						//java.util.Date cal = new java.util.Date();
						//java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
						//String timestring = sdf.format(cal.getTime());
						//double distance = getDistance(location.getLatitude(), 35.681368, location.getLongitude(), 139.766076); // 東京駅からの距離（距離が変わっているかの絶対指標として）
						//Toast.makeText(MainActivity.this, timestring + "【"+String.format("%.2f", distance)+"m】\n"+location.getLatitude()+"｜"+location.getLongitude() , Toast.LENGTH_LONG).show();
						
						// 高度および精度の情報を確認するためのデバッグ用コード
						//Toast.makeText(MainActivity.this, (location.hasAltitude() ? "高度あり｜" : "高度なし｜")+ location.getAltitude() + "\n" + (location.hasAccuracy() ? "精度あり｜" : "精度なし｜") + location.getAccuracy(), Toast.LENGTH_LONG).show();
						
						// ARchitectViewの位置情報を更新します。
						MainActivity.this.architectView.setLocation(
							location.getLatitude(),                                                // 緯度
							location.getLongitude(),                                               // 経度
							0.0,                                                    // 高度（m）。本来なら↓のようにすべきですが、常に「0.0」にすることで、POIの位置を調整しやすくしています。
							//(location.hasAltitude() ? location.getAltitude() : UNKNOWN_ALTITUDE),  // 高度（m）。高度として「UNKNOWN_ALTITUDE」値が使われると、ARchitect Worldでは現在のユーザーの高度情報で代用されます。
							(location.hasAccuracy() ? location.getAccuracy() : 20)                 // 精度（m）。精度は、10m以内だと高い、11～35mなら普通、35m以上なら低いと判断してください。
						);
					}
				}
			}
		};
		
		// 位置情報を収集するために使うLocationProviderに、位置情報リスナー（locationListener）を指定してインスタンスを生成・取得します。
		this.locationProvider = getLocationProvider(this.locationListener);
		
		// ※注意： 以下は、App Indexing API（＝アプリをGoogle検索に登録してもらうためのAPI）を実装するために、Android Studioにより自動生成されたコードです。
		// 詳細はこちらを参照（英語）： https://g.co/AppIndexing/AndroidStudio
		client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
	}

	/**
	 * ［アクティビティのライフサイクル］アクティビティが表示される時に呼び出されます。
	 */
	@Override
	protected void onStart() {
		super.onStart();
		
		// ※注意： 以下は、App Indexing API（＝アプリをGoogle検索に登録してもらうためのAPI）を実装するために、Android Studioにより自動生成されたコードです。
		// 詳細はこちらを参照（英語）： https://g.co/AppIndexing/AndroidStudio
		client.connect();
		Action viewAction = Action.newAction(
				Action.TYPE_VIEW, // TODO: アクションタイプを選択してください。
				"Wikitude Sample Main Page", // TODO: 表示されるコンテンツのタイトルを定義してください。
				// TODO: アプリのアクティビティコンテンツと合致するWebページコンテンツを持っているなら、自動生成されたWebページURLが正しいかどうか確認してください。持っていない場合は、URLに「null」を指定してください。
				Uri.parse("http://host/path"),
				// TODO: 自動生成されたアプリURLが正しいかどうか確認してください。
				Uri.parse("android-app://isshiki.mywikitudeappforandroid/http/host/path")
		);
		AppIndex.AppIndexApi.start(client, viewAction);
	}

	/**
	 * ［アクティビティのライフサイクル］アクティビティが作成＆表示完了した直後に呼び出されます。
	 */
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		
		if (this.architectView != null) {
			
			// ARchitectViewのライフサイクルメソッド「onPostCreate」を呼び出す必要があります。
			this.architectView.onPostCreate();
			
			try {
				// ARchitect World（＝HTMLファイル）を、メインコンポーネントであるARchitectViewにロードします。
				// ※Android向けでは、HTMLファイル内に「<script src="architect://architect.js"></script>」を必ず記載する必要があります。
				this.architectView.load(MAIN_ARCHITECT_WORLD_URL);
				
				// 対象範囲外の距離をARchitectViewに設定します。デフォルトでは50kmです。※本サンプルではARchitectWorldのメインロジックで手動で500m以内にさらに範囲限定しています。
				//this.architectView.setCullingDistance(CULLING_DISTANCE_METERS);  // ※デフォルトのままなのでコメントアウトしています。
				
				// ARchitect World内のARオブジェクトであるVideoDrawablesが、現在の端末でサポートされているかを検証し、できない場合はメッセージをトーストします。
				//if (!this.isVideoDrawablesSupported()) {
				//	Toast.makeText(this, R.string.videosrawables_fallback, Toast.LENGTH_LONG).show();
				//}
				
				// ARchitect Worldにデータを挿入します。
				//this.injectData(); // ※本サンプルではARchitectWorldのメインロジック内でデータを取得しているのでコメントアウトしています。これを本サンプルで試したい場合は、mainlogic.jsファイルのlocationChangedメソッドの中身を空にしてください。
				
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	/**
	 * ［アクティビティのライフサイクル］アクティビティがユーザー操作可能になる時に呼び出されます。
	 */
	@Override
	protected void onResume() {
		super.onResume();
		
		// ARchitectViewのライフサイクルメソッド「onResume」を呼び出す必要があります。
		if (this.architectView != null) {
			this.architectView.onResume();
			
			// ARchitectViewに精度リスナーを登録します。
			if (this.sensorAccuracyListener != null) {
				this.architectView.registerSensorAccuracyChangeListener(this.sensorAccuracyListener);
			}
		}
		
		// LocationProviderのライフサイクルメソッド「onResume」を呼び出す必要があります。通常、Resumeが通知されると位置情報の収集が再開され、ステータスバーのGPSインジケーターが点灯します。
		if (this.locationProvider != null) {
			this.locationProvider.onResume();
		}
	}

	/**
	 * ［アクティビティのライフサイクル］アクティビティがユーザー操作不可能になる時に呼び出されます。
	 */
	@Override
	protected void onPause() {
		super.onPause();
		
		// ARchitectViewのライフサイクルメソッド「onPause」を呼び出す必要があります。
		if (this.architectView != null) {
			this.architectView.onPause();
			
			// ARchitectViewから精度リスナーを登録解除します。
			if (this.sensorAccuracyListener != null) {
				this.architectView.unregisterSensorAccuracyChangeListener(this.sensorAccuracyListener);
			}
		}
		
		// LocationProviderのライフサイクルメソッド「onPause」を呼び出す必要があります。通常、Pauseが通知されると位置情報の収集が止まり、ステータスバーのGPSインジケーターが消えます。
		if (this.locationProvider != null) {
			this.locationProvider.onPause();
		}
	}

	/**
	 * ［アクティビティのライフサイクル］アクティビティが非表示になる時に呼び出されます。
	 */
	@Override
	protected void onStop() {
		super.onStop();
		
		// ※注意： 以下は、App Indexing API（＝アプリをGoogle検索に登録してもらうためのAPI）を実装するために、Android Studioにより自動生成されたコードです。
		// 詳細はこちらを参照（英語）： https://g.co/AppIndexing/AndroidStudio
		Action viewAction = Action.newAction(
				Action.TYPE_VIEW, // TODO: アクションタイプを選択してください。
				"Main Page", // TODO: 表示されるコンテンツのタイトルを定義してください。
				// TODO: アプリのアクティビティコンテンツと合致するWebページコンテンツを持っているなら、自動生成されたWebページURLが正しいかどうか確認してください。持っていない場合は、URLに「null」を指定してください。
				Uri.parse("http://host/path"),
				// TODO: 自動生成されたアプリURLが正しいかどうか確認してください。
				Uri.parse("android-app://isshiki.mywikitudeappforandroid/http/host/path")
		);
		AppIndex.AppIndexApi.end(client, viewAction);
		client.disconnect();
	}

	/**
	 * ［アクティビティのライフサイクル］アクティビティが非表示から表示になる時に呼び出されます。
	 */
	@Override
	protected void onRestart() {
		super.onRestart();
	}

	/**
	 * ［アクティビティのライフサイクル］アクティビティが破棄される時に呼び出されます。
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		// ARchitectViewのライフサイクルメソッド「onDestroy」を呼び出す必要があります。
		if (this.architectView != null) {
			this.architectView.onDestroy();
		}
	}

	/**
	 * ［警告通知］アプリ内のメモリ警告を検知した際に呼び出されます。。
	 */
	@Override
	public void onLowMemory() {
		super.onLowMemory();
		
		// ARchitectViewのライフサイクルメソッド「onLowMemory」を呼び出す必要があります。
		if ( this.architectView != null ) {
			this.architectView.onLowMemory();
		}
	}

	//endregion

	// region 各センサーへのアクセス権限に関する処理

	/**
	 * アプリの実行に必要な権限をチェックして、不足していればユーザーに要求します。
	 */
	private void RequestPermission() {
		
		List<String> permissionList = new ArrayList<String>();
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
			permissionList.add(Manifest.permission.CAMERA);
			Toast.makeText(this, "カメラが使えないと起動できません。", Toast.LENGTH_LONG).show();
		}
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
			Toast.makeText(this, "位置情報（GPS）が使えないと起動できません。", Toast.LENGTH_LONG).show();
		}
		if (permissionList.size() > 0) {
			String[] permissions = permissionList.toArray(new String[permissionList.size()]);
			int REQUEST_CODE_NONE = 0;  // onRequestPermissionResultオーバーライドメソッド内では何も処理しないので、特に意味の無い数値を指定しています。
			ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_NONE);
			
			// 30秒ほど、権限設定をチェックしながら待つ
			for (int i = 0; i < 300; i++)
			{
				if (isFinishing()) return;
				try {
					Thread.sleep(100);
					Thread.yield();
				} catch (InterruptedException e) {
					break;
				}
				
				if ((ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) &&
						(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
					return;
				}
			}
			
			// いったんアプリを終了します。
			Toast.makeText(this, "権限設定後に、もう一度アプリを起動し直してください。", Toast.LENGTH_LONG).show();
			this.finish();
		}
	}

	//endregion

	// region センサー精度の低下を管理（必要であればユーザーにコンパスのキャリブレーションを促す）

	/**
	 * ARchitectView用のセンサー精度変化リスナーとして指定するオブジェクトを生成して返します。URLリスナーの中には、コンパスの精度が変化したときに呼び出されるonCompassAccuracyChanged()メソッドを実装しています。
	 * @return architectView用のセンサー精度変化リスナー。
	 */
	protected ArchitectView.SensorAccuracyChangeListener getSensorAccuracyListener() {
		return new ArchitectView.SensorAccuracyChangeListener() {
			@Override
			public void onCompassAccuracyChanged(int accuracy) {
				// UNRELIABLE = 0, LOW = 1, MEDIUM = 2, HIGH = 3
				if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM &&
						MainActivity.this != null && !MainActivity.this.isFinishing() &&
						System.currentTimeMillis() - MainActivity.this.lastCalibrationToastShownTimeMillis > 5 * 1000) {
					Toast.makeText(MainActivity.this, R.string.compass_accuracy_low, Toast.LENGTH_LONG).show();
					MainActivity.this.lastCalibrationToastShownTimeMillis = System.currentTimeMillis();
				}
			}
		};
	}

	//endregion

	// region ARchitect World用「architectsdk://」プロトコル経由のURL呼び出しの管理（※本サンプルではコードはありますが実質的には使っていません）

	/**
	 *
	 * @return ARchitectView用のURLリスナー。
	 */
	public ArchitectView.ArchitectUrlListener getUrlListener() {
		return new ArchitectView.ArchitectUrlListener() {
			
			@Override
			public boolean urlWasInvoked(String uriString) {
				Uri invokedUri = Uri.parse(uriString);
				
				// ［スナップショット］ボタンが押されたときの処理。URIの内容をチェックして、ボタンごとに動作を振り分けたりできます。例えば「architectsdk://button?action=captureScreen」というURLで呼び出されているかどうかなど。
				if ("button".equalsIgnoreCase(invokedUri.getHost())) {
					MainActivity.this.architectView.captureScreen(ArchitectView.CaptureScreenCallback.CAPTURE_MODE_CAM_AND_WEBVIEW, new ArchitectView.CaptureScreenCallback() {
						
						@Override
						public void onScreenCaptured(final Bitmap screenCapture) {
							if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
								MainActivity.this.screenCapture = screenCapture;
								Toast.makeText(MainActivity.this, "ストレージが使えないとスクリーンキャプチャを保存できません。", Toast.LENGTH_LONG).show();
								ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WIKITUDE_PERMISSIONS_REQUEST_EXTERNAL_STORAGE);
							} else {
								MainActivity.this.saveScreenCaptureToExternalStorage(screenCapture);
							}
						}
					});
				}
				return true;
			}
		};
	}

	/**
	 * 外部ストレージにスクリーンキャプチャを保存します。
	 * @param screenCapture 保存するビットマップ画像。
	 */
	protected void saveScreenCaptureToExternalStorage(Bitmap screenCapture) {
		if (screenCapture != null) {
			// スクリーンキャプチャを外部のキャッシュディレクトリに保存します。
			final File screenCaptureFile = new File(Environment.getExternalStorageDirectory().toString(), "screenCapture_" + System.currentTimeMillis() + ".jpg");
			
			// 1. ビットマップをファイルに保存し、JPEGに圧縮します。PNGも使えます。
			try {
				
				final FileOutputStream out = new FileOutputStream(screenCaptureFile);
				screenCapture.compress(Bitmap.CompressFormat.JPEG, 90, out);
				out.flush();
				out.close();
				
				// 2. 送信インテントを作成します。
				final Intent share = new Intent(Intent.ACTION_SEND);
				share.setType("image/jpg");
				share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(screenCaptureFile));
				
				// 3. インテントchooser（ここでは共有の選択ダイアログ）を起動します。
				final String chooserTitle = "スナップショットを共有";
				MainActivity.this.startActivity(Intent.createChooser(share, chooserTitle));
				
			} catch (final Exception e) {
				// 全てのアクセス許可セットされていれば、このような例外は起こらないはず。
				MainActivity.this.runOnUiThread(new Runnable() {
					
					@Override
					public void run() {
						Toast.makeText(MainActivity.this, "予期しないエラー, " + e, Toast.LENGTH_LONG).show();
					}
				});
			}
		}
	}

	//endregion

	// region 位置情報更新の管理（※LocationProviderに一任しており、ここではそのプロバイダーを生成するのみです）

	/**
	 * LocationProviderを取得します。
	 * @param locationListener システムの位置情報リスナーを指定してください。
	 * @return
	 */
	public ILocationProvider getLocationProvider(final LocationListener locationListener) {
		return new LocationProvider(this, locationListener);
	}

	//endregion

	// region 位置情報の初期データ取得＆ARchitect Worldへの挿入（※本サンプルではコードはありますが実質的には使っていません）

	/**
	 * ARchitect WorldのJavaScriptメソッドを呼び出すことにより、POIデータを挿入します。
	 */
	protected void injectData() {
		if (!isLoading) {
			final Thread t = new Thread(new Runnable() {
				
				@Override
				public void run() {
					
					isLoading = true;
					
					final int WAIT_FOR_LOCATION_STEP_MS = 2000;
					while (lastKnownLocaton == null && !isFinishing()) {
						// ユーザー位置情報が確定していない場合は、ここで位置情報を取得中である（＝onLocationChangedで行っている）ことをトースト表示します。
						runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast.makeText(MainActivity.this, R.string.location_fetching, Toast.LENGTH_SHORT).show();
							}
						});
						try {
							Thread.sleep(WAIT_FOR_LOCATION_STEP_MS);
						} catch (InterruptedException e) {
							break;
						}
					}
					
					if (lastKnownLocaton != null && !isFinishing()) {
						// POIデータを取得して、ARchitect Worldに渡します。
						// TODO: データ取得はダミー実装になっていますので、例えばデータベースから取得するなど、適切なデータロード処理に置き換えてください。
						poiData = getPoiInformation(lastKnownLocaton, 20);
						callJavaScript("World.loadPoisFromJsonData", new String[]{poiData.toString()});
					}
					
					isLoading = false;
				}
			});
			t.start();
		}
	}

	/**
	 * POI情報をロードして、それをJSONArray型で返します。JavaScript側から簡単にデータにアクセスできるように属性名を指定してください。
	 *
	 * @param userLocation   the location of the user
	 * @param numberOfPlaces number of places to load (at max)
	 * @return POI information in JSONArray
	 */
	public static JSONArray getPoiInformation(final Location userLocation, final int numberOfPlaces) {
		
		if (userLocation == null) {
			return null;
		}
		
		final JSONArray pois = new JSONArray();
		
		// POIデータを抽出する際にJavaScriptコードから使われる属性名を以下のように定義しています。
		final String ATTR_ID = "id";
		final String ATTR_NAME = "name";
		final String ATTR_LATITUDE = "latitude";
		final String ATTR_LONGITUDE = "longitude";
		final String ATTR_ALTITUDE = "altitude";
		final String ATTR_DISTANCE = "distance";
		
		// 必要な数のPOIデータを（ランダムな位置や文字列で）生成します。
		for (int i = 1; i <= numberOfPlaces; i++) {
			
			double[] poiLocationLatLon = getRandomLatLonNearby(userLocation.getLatitude(), userLocation.getLongitude());
			double distance = getDistance(poiLocationLatLon[0], userLocation.getLatitude(), poiLocationLatLon[1], userLocation.getLongitude());
			BigDecimal decimalKm = new BigDecimal(distance / 1000);
			String distanceString = (distance > 999) ? (decimalKm.setScale(2, BigDecimal.ROUND_HALF_UP)  + " km") : (Math.round(distance) + " m");
			
			// 高度として「UNKNOWN_ALTITUDE」値が使われると、ARchitect Worldでは現在のユーザーの高度情報で代用されます。
			// 正確なPOI属性値を使用するために、LocationManager（＝LocationProvider.javaファイル内）などでは適切に高度情報を処理するように注意してください（例えばGPS精度が7m以下の場合だけ高度を渡すなど）。
			// 精度は、10m以内だと高い、11～35mなら普通、35m以上なら低いと判断してください。
			JSONObject singlePoiInfo = new JSONObject();
			try {
				singlePoiInfo.accumulate(ATTR_ID, String.valueOf(i));
				singlePoiInfo.accumulate(ATTR_NAME, "POI#" + i);
				singlePoiInfo.accumulate(ATTR_LATITUDE, poiLocationLatLon[0]);
				singlePoiInfo.accumulate(ATTR_LONGITUDE, poiLocationLatLon[1]);
				singlePoiInfo.accumulate(ATTR_ALTITUDE, UNKNOWN_ALTITUDE);
				singlePoiInfo.accumulate(ATTR_DISTANCE, distanceString);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			pois.put(singlePoiInfo);
		}
		
		return pois;
	}

	/**
	 * ダミーの位置情報を生成するヘルパー関数。
	 *
	 * @param lat 中心の緯度。
	 * @param lon 中心の経度。
	 * @return 与えられた位置情報近辺の緯度／経度をランダムに計算して返します。
	 */
	private static double[] getRandomLatLonNearby(final double lat, final double lon) {
		return new double[]{lat + Math.random() /5 - 0.1, lon + Math.random() / 5 - 0.1};
	}

	//endregion

	// region 共通のプライベートメソッド（※本サンプルではコードはありますが、いずれも実質的には使っていません）

	/**
	 * ネイティブコードであるARchitectView側から、HTMLコードであるARchitect World内のJavaScriptメソッドを呼び出します。
	 *
	 * @param methodName 呼び出し対象のJavaScriptメソッド名
	 * @param arguments メソッドへの引数
	 */
	private void callJavaScript(final String methodName, final String[] arguments) {
		final StringBuilder argumentsString = new StringBuilder("");
		for (int i = 0; i < arguments.length; i++) {
			argumentsString.append(arguments[i]);
			if (i < arguments.length - 1) {
				argumentsString.append(", ");
			}
		}
		
		if (this.architectView != null) {
			final String js = (methodName + "( " + argumentsString.toString() + " );");
			this.architectView.callJavascript(js);
		}
	}

	/**
	 * ARchitect World内のARオブジェクトであるVideoDrawablesが、現在の端末でサポートされているかを検証するためのヘルパーメソッド。ARchitect WorldsでVideoDrawablesを起動する前に実行するのをオススメします。
	 *
	 * @return true：VideoDrawablesがサポートされている。false： サポートされていないので、ビデオがするスクリーン表示でレンダリングされることになります。
	 */
	public static final boolean isVideoDrawablesSupported() {
		String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
		return extensions != null && extensions.contains("GL_OES_EGL_image_external");
	}

	/**
	 * ユーザー地点から対象地点までの距離をメートルで算出します。
	 *
	 * @param targetLatitude 対象地点の緯度。
	 * @param centerPointLatitude ユーザー地点の緯度。
	 * @param targetLongtitude 対象地点の経度。
	 * @param centerPointLongitude ユーザー地点の経度。
	 * @return
	 */
	public static double getDistance(double targetLatitude, double centerPointLatitude, double targetLongtitude, double centerPointLongitude) {
		// 参考：http://www.movable-type.co.uk/scripts/latlong.html
		double Δφ = (centerPointLatitude - targetLatitude) * Math.PI / 180;
		double Δλ = (centerPointLongitude - targetLongtitude) * Math.PI / 180;
		double a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) + Math.cos(targetLatitude * Math.PI / 180) * Math.cos(centerPointLatitude * Math.PI / 180) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return 6371e3 * c;
	}

	//endregion

}

