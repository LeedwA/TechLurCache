
/**
 * Created by zj on 2016/7/29.
 *
 * 对json数据进行缓存，具体缓存逻辑需配合网络框架，以DiskLruCache缓存为主
 * 缓存逻辑：优先从内存中获取(LruCache)，内存中没有就从SD卡(DiskLruCache)中获取，如果SD卡中没有最后再发送网络请求
 * 当请求网络获取之后  需将数据缓存进两种缓存位置
 *
 */
public class DiskLruCacheUtils {
    private static     DiskLruCacheUtils diskLruCacheUtils;
    /**
     * 用于json缓存到内存中
     */
    private LruCache<String, String> jsonMemoryCache;

    /**
     * 缓存json数据的DiskLruCache实例
     */
    private DiskLruCache jsonDiskLruCache;

    /**
     *json字符串数据
     */
    private String data ;

    private Context mContext;
    /**
     * 缓存json的目录
     */
    private File cacheDir;



    /**
     *
     * @param
     * @param fileName 创建缓存的文件名
     * @param maxSize  最大的缓存空间  单位：MB
     */
    public DiskLruCacheUtils(Application application, String fileName, long maxSize) {
        this.mContext =application ;
        // 获取应用程序最大可用内存
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        //json缓存占用的内存空间
        int jsonCacheSize = maxMemory /16;
        // 设置json缓存大小为程序最大可用内存的1/16
        jsonMemoryCache = new LruCache<String, String>(jsonCacheSize) {
            @Override
            protected int sizeOf(String key, String json) {
                return json.getBytes().length;
            }
        };
        try {
            // 获取json串缓存路径
            cacheDir = getDiskCacheDir(mContext, fileName);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            // 创建DiskLruCache实例，初始化缓存数据
            jsonDiskLruCache = DiskLruCache.open(cacheDir, getAppVersion(mContext), 1, maxSize*1024*1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 方法描述：将传入的json串写入本地sd卡，即通过DiskLruCache
     *<p>
     * @param json_data 具体的json数据串
     * @param key 取数据时的需要提供的key
     * @return 无
     */

    private void saveJsonData2DiskLruCache(final String json_data, final String key) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String hashKey = hashKeyForDisk(key);
                    DiskLruCache.Editor editor = jsonDiskLruCache.edit(hashKey);
                    if (editor != null) {
                        OutputStream data = editor.newOutputStream(0);
                        if (writeJsonToDisk(json_data, data)) {
                            editor.commit();
                        } else {
                            editor.abort();
                        }
                        jsonDiskLruCache.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 将一个json串存储到LruCache中。
     *
     * @param key
     *            LruCache的键，传入缓存key
     * @param json
     *            LruCache的值，传入具体的json串。
     */
    public void saveJson2MemoryCache(String json,String key ) {
        if (getJsonFromMemoryCache(key) == null&&json!=null) {
            jsonMemoryCache.put(key, json);
        }
    }

    /**
     * 方法描述：通过key读取DiskLruCache里存入的缓存
     *<p>
     * @param
     * @return 通过key读取的缓存 如果没有  返回null
     */
    public String getJsonFromDiskLruCache(final String key){
        try {
            DiskLruCache.Snapshot snapShot = jsonDiskLruCache.get(hashKeyForDisk(key));
            if (snapShot != null) {
                InputStream is = snapShot.getInputStream(0);
                data = Inputstr2Str_Reader(is);//InputString转成String
                return data;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 方法描述：通过key读取内存MemoryCache里存入的缓存
     *
     * @param key LruCache的键，传入缓存key。
     * @return 对应传入键的json串数据。
     */
    public String getJsonFromMemoryCache(String key) {
        return jsonMemoryCache.get(key);
    }

    /**
     * 将json写入SD卡文件目录下
     * @param jsonString 要保存的json串
     * @param outputStream 要保存的输出位置
     * */
    private boolean writeJsonToDisk(String jsonString, OutputStream outputStream) {
        HttpURLConnection urlConnection = null;
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        try {
            in = new BufferedInputStream(new ByteArrayInputStream(jsonString.getBytes()), 8 * 1024);
            out = new BufferedOutputStream(outputStream, 8 * 1024);
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            return true;
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 方法描述：将缓存记录同步到DiskLruCache的journal文件中。通常在onPause方法中调用
     *<p>
     * @param
     * @return
     */
    public void fluchCache() {
        if (jsonDiskLruCache != null) {
            try {
                jsonDiskLruCache.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    /**
    * 方法描述：DiskLruCache关闭掉，是和open()方法对应的一个方法。
    * 关闭掉了之后就不能再调用DiskLruCache中任何操作缓存数据的方法，通常只在onDestroy方法中调用
    *<p>
    * @param
    * @return
            */
    public void closeCache(){
        if (jsonDiskLruCache != null) {
            try {
                jsonDiskLruCache.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * 使用MD5算法对传入的key进行加密并返回。
     */
    public String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    /**
     *
     * @param context
     * @param uniqueName
     * @return sFile
     */
    public File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath =context.getExternalCacheDir().getPath();
        } else {
            cachePath =context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    /**
     * 获取当前应用程序的版本号。
     */
    public int getAppVersion(Context context) {
        try {
            PackageInfo info =context.getPackageManager().getPackageInfo(context.getPackageName(),
                    0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    /**
     * 方法描述：inputStream转为String
     * @param in
     * @return String
     */
    public static String Inputstr2Str_Reader(InputStream in)
    {
        String str = "";
        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "utf-8"));
            StringBuffer sb = new StringBuffer();
            while ((str = reader.readLine()) != null)
            {
                sb.append(str).append("\n");
            }
            return sb.toString();
        }
        catch (UnsupportedEncodingException e1)
        {
            e1.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return str;
    }

}
