package com.llw.goodweather;

import android.Manifest;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.llw.goodweather.adapter.WeatherForecastAdapter;
import com.llw.goodweather.bean.LifeStyleResponse;
import com.llw.goodweather.bean.TodayResponse;
import com.llw.goodweather.bean.WeatherForecastResponse;
import com.llw.goodweather.contract.WeatherContract;
import com.llw.goodweather.utils.StatusBarUtil;
import com.llw.goodweather.utils.ToastUtils;
import com.llw.mvplibrary.mvp.MvpActivity;
import com.llw.mvplibrary.utils.ObjectUtils;
import com.llw.mvplibrary.view.WhiteWindmills;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Response;

public class MainActivity extends MvpActivity<WeatherContract.WeatherPresenter> implements WeatherContract.IWeatherView {

    @BindView(R.id.tv_info)
    TextView tvInfo;//天气状况
    @BindView(R.id.tv_temperature)
    TextView tvTemperature;//温度
    @BindView(R.id.tv_low_height)
    TextView tvLowHeight;//最高温和最低温
    @BindView(R.id.tv_city)
    TextView tvCity;//城市
    @BindView(R.id.tv_old_time)
    TextView tvOldTime;//最近更新时间
    @BindView(R.id.rv)
    RecyclerView rv;//天气列表显示

    @BindView(R.id.tv_comf)
    TextView tvComf;//舒适度
    @BindView(R.id.tv_trav)
    TextView tvTrav;//旅游指数
    @BindView(R.id.tv_sport)
    TextView tvSport;//运动指数
    @BindView(R.id.tv_cw)
    TextView tvCw;//洗车指数
    @BindView(R.id.tv_air)
    TextView tvAir;//空气指数
    @BindView(R.id.tv_drsg)
    TextView tvDrsg;//穿衣指数
    @BindView(R.id.tv_flu)
    TextView tvFlu;//感冒指数

    @BindView(R.id.ww_big)
    WhiteWindmills wwBig;//大风车
    @BindView(R.id.ww_small)
    WhiteWindmills wwSmall;//小风车
    @BindView(R.id.tv_wind_direction)
    TextView tvWindDirection;//风向
    @BindView(R.id.tv_wind_power)
    TextView tvWindPower;//风力

    private RxPermissions rxPermissions;//权限请求框架
    //定位器
    public LocationClient mLocationClient = null;
    private MyLocationListener myListener = new MyLocationListener();


    //数据初始化  主线程，onCreate方法可以删除了，把里面的代码移动这个initData下面
    @Override
    public void initData(Bundle savedInstanceState) {
        //因为这个框架里面已经放入了绑定，所以这行代码可以注释掉了。
        //ButterKnife.bind(this);
        StatusBarUtil.transparencyBar(context);//透明状态栏
         initList();//天气预报列表初始化
        rxPermissions = new RxPermissions(this);//实例化这个权限请求框架，否则会报错
        permissionVersion();//权限判断
    }

    //绑定布局文件
    @Override
    public int getLayoutId() {
        return R.layout.activity_main;
    }

    //绑定Presenter ，这里不绑定会报错
    @Override
    protected WeatherContract.WeatherPresenter createPresent() {
        return new WeatherContract.WeatherPresenter();
    }

    //权限判断
    private void permissionVersion() {
        if (Build.VERSION.SDK_INT >= 23) {//6.0或6.0以上
            //动态权限申请
            permissionsRequest();
        } else {//6.0以下
            //发现只要权限在AndroidManifest.xml中注册过，均会认为该权限granted  提示一下即可
            ToastUtils.showShortToast(this, "你的版本在Android6.0以下，不需要动态申请权限。");
        }
    }

    //动态权限申请
    private void permissionsRequest() {//使用这个框架需要制定JDK版本，建议用1.8
        rxPermissions.request(Manifest.permission.ACCESS_FINE_LOCATION)
                .subscribe(granted -> {
                    if (granted) {//申请成功
                        //得到权限之后开始定位
                        startLocation();
                    } else {//申请失败
                        ToastUtils.showShortToast(this, "权限未开启");
                    }
                });
    }

    //定位
    private void startLocation() {
        //声明LocationClient类
        mLocationClient = new LocationClient(this);
        //注册监听函数
        mLocationClient.registerLocationListener(myListener);
        LocationClientOption option = new LocationClientOption();

        //如果开发者需要获得当前点的地址信息，此处必须为true
        option.setIsNeedAddress(true);
        //可选，设置是否需要最新版本的地址信息。默认不需要，即参数为false
        option.setNeedNewVersionRgc(true);
        //mLocationClient为第二步初始化过的LocationClient对象
        //需将配置好的LocationClientOption对象，通过setLocOption方法传递给LocationClient对象使用
        mLocationClient.setLocOption(option);
        //启动定位
        mLocationClient.start();

    }


    /**
     * 定位结果返回
     */
    private class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            //获取区/县
            String district = location.getDistrict();
            //获取今天的天气数据
            mPresent.todayWeather(context,district);
            mPresent.weatherForecast(context,district);//获取天气预报数据
            mPresent.lifeStyle(context,district);//获取生活指数
        }
    }

    //查询当天天气，请求成功后的数据返回@BindView(R.id.rv) 之后
    @Override
    public void getTodayWeatherResult(Response<TodayResponse> response) {
        //数据返回后关闭定位
        mLocationClient.stop();
        if (response.body().getHeWeather6().get(0).getBasic() != null) {//得到数据不为空则进行数据显示
            //数据渲染显示出来
            tvTemperature.setText(response.body().getHeWeather6().get(0).getNow().getTmp());//温度
            tvCity.setText(response.body().getHeWeather6().get(0).getBasic().getLocation());//城市
            tvInfo.setText(response.body().getHeWeather6().get(0).getNow().getCond_txt());//天气状况
            tvOldTime.setText("上次更新时间：" + response.body().getHeWeather6().get(0).getUpdate().getLoc());

            tvWindDirection.setText("风向     " + response.body().getHeWeather6().get(0).getNow().getWind_dir());//风向
            tvWindPower.setText("风力     " + response.body().getHeWeather6().get(0).getNow().getWind_sc() + "级");//风力
            wwBig.startRotate();//大风车开始转动
            wwSmall.startRotate();//小风车开始转动

        } else {
            ToastUtils.showShortToast(context, response.body().getHeWeather6().get(0).getStatus());
        }
    }



    //数据请求失败返回
    @Override
    public void getDataFailed() {
        ToastUtils.showShortToast(context,"网络异常");//这里的context是框架中封装好的，等同于this
    }


    List<WeatherForecastResponse.HeWeather6Bean.DailyForecastBean> mList;//初始化数据源
    WeatherForecastAdapter mAdapter;//初始化适配器
    /**
     * 初始化天气预报数据列表
     */
    private void initList() {
        mList = new ArrayList<>();//声明为ArrayList
        mAdapter = new WeatherForecastAdapter(R.layout.item_weather_forecast_list, mList);//为适配器设置布局和数据源
        LinearLayoutManager manager = new LinearLayoutManager(context);//布局管理,默认是纵向
        rv.setLayoutManager(manager);//为列表配置管理器
        rv.setAdapter(mAdapter);//为列表配置适配器
    }
    //查询天气预报，请求成功后的数据返回
    @Override
    public void getWeatherForecastResult(Response<WeatherForecastResponse> response) {
        if (("ok").equals(response.body().getHeWeather6().get(0).getStatus())) {
            //最低温和最高温
            tvLowHeight.setText(response.body().getHeWeather6().get(0).getDaily_forecast().get(0).getTmp_min() + " / " +
                    response.body().getHeWeather6().get(0).getDaily_forecast().get(0).getTmp_max() + "℃");

            if (response.body().getHeWeather6().get(0).getDaily_forecast() != null) {
                List<WeatherForecastResponse.HeWeather6Bean.DailyForecastBean> data
                        = response.body().getHeWeather6().get(0).getDaily_forecast();
                mList.clear();//添加数据之前先清除
                mList.addAll(data);//添加数据
                mAdapter.notifyDataSetChanged();//刷新列表
            } else {
                ToastUtils.showShortToast(context, "天气预报数据为空");
            }
        } else {
            ToastUtils.showShortToast(context, response.body().getHeWeather6().get(0).getStatus());
        }
    }

    //查询生活指数，请求成功后的数据返回
    @Override
    public void getLifeStyleResult(Response<LifeStyleResponse> response) {
        if(("ok").equals(response.body().getHeWeather6().get(0).getStatus())){
            List<LifeStyleResponse.HeWeather6Bean.LifestyleBean> data = response.body().getHeWeather6().get(0).getLifestyle();
            if(!ObjectUtils.isEmpty(data)){
                for (int i = 0;i<data.size();i++){
                    if(("comf").equals(data.get(i).getType())){
                        tvComf.setText("舒适度："+data.get(i).getTxt());
                    }else if(("drsg").equals(data.get(i).getType())){
                        tvDrsg.setText("穿衣指数："+data.get(i).getTxt());
                    }else if(("flu").equals(data.get(i).getType())){
                        tvFlu.setText("感冒指数："+data.get(i).getTxt());
                    }else if(("sport").equals(data.get(i).getType())){
                        tvSport.setText("运动指数："+data.get(i).getTxt());
                    }else if(("trav").equals(data.get(i).getType())){
                        tvTrav.setText("旅游指数："+data.get(i).getTxt());
                    }else if(("cw").equals(data.get(i).getType())){
                        tvCw.setText("洗车指数："+data.get(i).getTxt());
                    }else if(("air").equals(data.get(i).getType())){
                        tvAir.setText("空气指数："+data.get(i).getTxt());
                    }
                }

            }else {
                ToastUtils.showShortToast(context, "生活指数数据为空");
            }
        }else {
            ToastUtils.showShortToast(context, response.body().getHeWeather6().get(0).getStatus());
        }
    }
    /**
     * 页面销毁时
     */
    @Override
    public void onDestroy() {
        wwBig.stop();//停止大风车
        wwSmall.stop();//停止小风车
        super.onDestroy();
    }


}

