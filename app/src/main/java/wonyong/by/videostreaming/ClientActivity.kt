package wonyong.by.videostreaming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_client.*
import kotlinx.android.synthetic.main.activity_wifi_direct.*
import java.net.InetAddress

class ClientActivity : AppCompatActivity(), ClientTaskListener {

    //위젯 정보 시작
    lateinit var widgetConnectButton : Button
    lateinit var widgetStatusTextView : TextView
    lateinit var widgetDisconnectButton : Button
    lateinit var widgetRefreshButton : Button
    lateinit var widgetTitle : TextView
    //위젯정보 끝


    lateinit var hostAddress : InetAddress
    lateinit var task : ClientNetworkTask
    var deviceList = arrayListOf<DeviceInfo>()
    lateinit var wifiManager:WifiManager
    lateinit var wifiP2pManager : WifiP2pManager;
    lateinit var wifiP2pChannel : WifiP2pManager.Channel
    lateinit var broadcastReceiver : BroadcastReceiver
    lateinit var intentFilter : IntentFilter
    lateinit var deviceNameArray:Array<String?>
    lateinit var deviceArray:Array<WifiP2pDevice?>
    lateinit var deviceInfo: DeviceInfo
    val CONST = Consts()
    var connectedDevice = 0
    var peers:ArrayList<WifiP2pDevice> = ArrayList<WifiP2pDevice>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)

        getInfo()
        init()
        buttonListener()

    }

    private fun getInfo() {
        deviceInfo = intent.getSerializableExtra("deviceInfo") as DeviceInfo
    }



    private fun buttonListener() {
        clientWifiDirectConnectButton.setOnClickListener {
            if (wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(false)
                clientWifiDirectConnectButton.setText("Wifi-On")
            } else {
                wifiManager.setWifiEnabled(true)
                clientWifiDirectConnectButton.setText("Wifi-Off")
            }
        }

        clientWifiDirectTcpButton.setOnClickListener {
            callAsyncTask(CONST.L_WAITING_RECEIVE)
        }

        clientWifiDirectRefreshButton.setOnClickListener {
            if(ContextCompat.checkSelfPermission(this@ClientActivity,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this@ClientActivity,
                    arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION),
                    100)
            }

            else {
                wifiP2pManager.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        clientWifiDirectConnectionStatus.setText("검색중")
                    }

                    override fun onFailure(i: Int) {
                        clientWifiDirectConnectionStatus.setText("검색실패")
                    }
                })
            }
        }

        clientWifiDirectListView.setOnItemClickListener(object : AdapterView.OnItemClickListener{
            override fun onItemClick(adapterView: AdapterView<*>, view : View?, i: Int, l: Long) {
                val device: WifiP2pDevice? = deviceArray[i]
                var config : WifiP2pConfig = WifiP2pConfig()
                config.deviceAddress = device?.deviceAddress
                config.wps.setup = WpsInfo.PBC
                config.groupOwnerIntent = 0

                wifiP2pManager.connect(wifiP2pChannel, config, object : WifiP2pManager.ActionListener{
                    override fun onSuccess() {
                        Toast.makeText(applicationContext, device?.deviceName+" 로 연결합니다.", Toast.LENGTH_SHORT).show()
                    }

                    override fun onFailure(p0: Int) {
                        Toast.makeText(applicationContext, "연결불가", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        })


        clientWifiDirectDisconnectButton.setOnClickListener {
            //TODO Group Owner일 경우 연결끊으면 어떻게 해야하는지
            wifiP2pManager.cancelConnect(wifiP2pChannel, object : WifiP2pManager.ActionListener{
                override fun onSuccess() {
                }

                override fun onFailure(p0: Int) {

                }
            })

            wifiP2pManager.removeGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener{
                override fun onSuccess() {
                    Toast.makeText(this@ClientActivity, "연결이 해제됩니다.", Toast.LENGTH_SHORT).show()
                }

                override fun onFailure(p0: Int) {

                }
            })
        }

        clientWifiDirectFindVideoButton.setOnClickListener {

        }
    }

    private fun init() {
        wifiManager = application.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        wifiP2pChannel = wifiP2pManager.initialize(this, Looper.getMainLooper(), null)
        broadcastReceiver = ClientBroadcastReceiver(wifiP2pManager, wifiP2pChannel, this)
        intentFilter = IntentFilter()
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        widgetStatusTextView = clientWifiDirectConnectionStatus
        widgetConnectButton = clientWifiDirectConnectButton
        widgetDisconnectButton = clientWifiDirectDisconnectButton
        widgetRefreshButton = clientWifiDirectRefreshButton
        widgetTitle = clientWifiDirectTitle


    }

    var peerListListener : WifiP2pManager.PeerListListener = object : WifiP2pManager.PeerListListener{
        override fun onPeersAvailable(peerList: WifiP2pDeviceList){

            if(!peerList.deviceList.equals(peers)){

                peers.clear()
                peers.addAll(peerList.deviceList)

                deviceNameArray = arrayOfNulls<String>(peerList.deviceList.size)
                deviceArray = arrayOfNulls<WifiP2pDevice>(peerList.deviceList.size)
                var index = 0
                for(device : WifiP2pDevice in peerList.deviceList){
                    deviceNameArray[index] = device.deviceName
                    deviceArray[index] = device
                    index++
                }

                var nameAdapter : ArrayAdapter<String> = ArrayAdapter<String>(applicationContext, android.R.layout.simple_list_item_1, deviceNameArray)
                clientWifiDirectListView.setAdapter(nameAdapter)
            }
            if(peers.size == 0){

                return
            }
        }
    }

    var connectInfoListener : WifiP2pManager.ConnectionInfoListener = object : WifiP2pManager.ConnectionInfoListener{
        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            hostAddress = wifiP2pInfo.groupOwnerAddress
            if(wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner){
                widgetStatusTextView.setText("server")
            }
            else if(wifiP2pInfo.groupFormed){
                widgetStatusTextView.setText("Client")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(broadcastReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        //TODO 마찬가지로 그룹오너인 경우 연결끊으면 어떻게 해야할지
        wifiP2pManager.cancelConnect(wifiP2pChannel, object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
            }

            override fun onFailure(p0: Int) {

            }
        })

        wifiP2pManager.removeGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                Toast.makeText(this@ClientActivity, "연결이 해제됩니다.", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(p0: Int) {

            }
        })

    }

    fun callAsyncTask(mode:String){
        task = ClientNetworkTask(mode, hostAddress, this)
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    override fun onWait() {
        callAsyncTask(CONST.L_WAITING_RECEIVE)
    }


}
