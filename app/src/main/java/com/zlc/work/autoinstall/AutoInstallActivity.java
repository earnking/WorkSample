package com.zlc.work.autoinstall;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.zlc.work.R;
import com.zlc.work.util.OemInstallUtil;
import com.zlc.work.util.SettingsUtil;
import com.zlc.work.util.ToastCompat;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * author: liuchun
 * date: 2018/10/16
 */
public class AutoInstallActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQ_STORAGE = 100;
    //private static final String APK_URL = "https://c1412a00137604931389140a17b04c66.dd.cdntips.com/imtt.dd.qq.com/16891/1B2B0537456F2383B2DEAE230B65F902.apk?mkey=5be3b95665e32a08&f=9870&fsname=com.tencent.news_5.6.90_5690.apk&csr=1bbd&cip=101.227.12.253&proto=https";

    private static final String APK_URL = "https://apkcdn.dsgame.iqiyi.com/cardgame/upload/unite/game/20181107/8300_1541581913_hmw_1.apk";

    @BindView(R.id.vivo_account) EditText vivoAccountInput;
    @BindView(R.id.vivo_password) EditText vivoPwdInput;
    @BindView(R.id.vivo_group) LinearLayout vivoGroup;

    @BindView(R.id.apk_list) RecyclerView mRecyclerView;
    private ApkRecyclerAdapter mAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accessibility);
        ButterKnife.bind(this);

        findViews();
    }

    private void findViews() {
        String model = Build.MODEL;
        if ("vivo X21".equalsIgnoreCase(model)) {
            vivoGroup.setVisibility(View.VISIBLE);
        } else {
            vivoGroup.setVisibility(View.GONE);
        }

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new ApkRecyclerAdapter(this);
        mRecyclerView.setAdapter(mAdapter);
        collectApkInfos();

        OemInstallUtil.downloadApkByPartner(this, APK_URL);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.jump_setting:
                jumpToSettingsIfNeed();
                break;
            case R.id.install_apk:
                installApk();
                break;
            default:break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //installApk();
            collectApkInfos();
        }
    }


    private void collectApkInfos() {
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }

        CollectApkTask task = new CollectApkTask();
        task.execute();
    }

    private void startVpnService() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 100);
        }

        Intent vpnIntent = new Intent(this, LocalVpnService.class);
        startService(vpnIntent);
    }

    private void installApk() {
//        if (jumpToSettingsIfNeed()) {
//            return;
//        }
        // 开启了自动安装辅助功能, 准备安装apk
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        //startVpnService();

        File extDir = Environment.getExternalStorageDirectory();
        if (extDir != null && extDir.exists() && extDir.canRead()) {
            File[] files = extDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    String name = pathname.getName();
                    return pathname.isFile() && name.endsWith(".apk") && (name.contains("xiaoxiaole_game"));
                }
            });

            if (files != null && files.length > 0) {
                File apkFile = files[0];
                installApkIfNeed(apkFile);
            }
        }
    }

    private void installApkIfNeed(File apkFile) {
        PackageManager pm = getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_ACTIVITIES);
        String pkgName = pi.packageName;
        long versionCode = pi.versionCode;

        PackageInfo prePi = null;
        try {
            prePi = pm.getPackageInfo(pkgName, PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        if (prePi == null || prePi.versionCode < versionCode) {
            // 未安装过或者是低版本
            OemInstallUtil.installApkFile(this, apkFile);
        } else {
            String name = prePi.applicationInfo.loadLabel(pm).toString();
            ToastCompat.makeText(this, name + "已经安装过了", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean jumpToSettingsIfNeed() {
        if (!SettingsUtil.isUnknownInstallAllowed(this)) {
            ToastCompat.makeText(this, "跳转设置页面，请打开允许安装未知来源选项", Toast.LENGTH_SHORT).show();
            SettingsUtil.jumpToSettingSecure(this);
            return true;
        }

        if (!SettingsUtil.isAccessibilityServiceEnable(this, AutoInstallService.class)) {
            ToastCompat.makeText(this, "跳转设置页面，请打开自动安装辅助功能", Toast.LENGTH_SHORT).show();
            SettingsUtil.jumpToSettingAccessibility(this);
            return true;
        }
        return false;
    }


    public class CollectApkTask extends AsyncTask<Void, Void, List<ApkItem>> {
        @Override
        protected List<ApkItem> doInBackground(Void... voids) {

            List<ApkItem> result = new ArrayList<>();
            File rootDir = Environment.getExternalStorageDirectory();
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                scanDir(rootDir, result);
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<ApkItem> list) {
            mAdapter.setData(list);
        }

        private void scanDir(File target, List<ApkItem> container) {

            File[] files = target.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    String name = pathname.getName();
                    return pathname.isFile() && name.endsWith(".apk");
                }
            });
            if (files != null && files.length > 0) {
                for (File file : files) {
                    ApkItem item = new ApkItem(AutoInstallActivity.this, file.getAbsolutePath());
                    container.add(item);
                }
            }
//            if (target.isDirectory()) {
//                File[] files = target.listFiles();
//                if (files == null || files.length <= 0) {
//                    return;
//                }
//
//                for (File file : files) {
//                    scanDir(file, container);
//                }
//            } else if (target.isFile() && target.getName().endsWith(".apk")) {
//                ApkItem item = new ApkItem(AutoInstallActivity.this, target.getAbsolutePath());
//                container.add(item);
//            }
        }
    }
}
