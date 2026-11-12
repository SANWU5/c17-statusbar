package android.net;
public class ConnectivityManager {
    public Network active;
    public NetworkCapabilities capabilities;
    public Network getActiveNetwork(){return active;}
    public NetworkCapabilities getNetworkCapabilities(Network network){return network==active?capabilities:null;}
    public static class NetworkCallback {
        public void onAvailable(Network network) { }
        public void onLost(Network network) { }
        public void onCapabilitiesChanged(Network network,NetworkCapabilities capabilities) { }
        public void onUnavailable() { }
    }
    public void registerNetworkCallback(NetworkRequest request,NetworkCallback callback,android.os.Handler handler) { }
    public void registerDefaultNetworkCallback(NetworkCallback callback,android.os.Handler handler) { }
}
