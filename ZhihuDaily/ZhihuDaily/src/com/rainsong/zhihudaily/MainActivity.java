package com.rainsong.zhihudaily;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.widget.ListView;

import com.google.gson.Gson;
import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;
import com.rainsong.zhihudaily.NewsListEntity.NewsEntity;
import com.rainsong.zhihudaily.db.NewsDataSource;
import com.rainsong.zhihudaily.util.GsonUtils;
import com.rainsong.zhihudaily.util.ListUtils;
import com.rainsong.zhihudaily.util.ZhihuUtils;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    // 获取最新新闻
    public static final String URL_LATEST = "http://news-at.zhihu.com/api/4/news/latest";
    // 获取过往新闻
    public static final String URL_BEFORE = "http://news.at.zhihu.com/api/4/news/before/";

    private Context mContext;
    private PullToRefreshListView mPullToRefreshListView;
    private NewsAdapter mAdapter;
    private String mCurrentDate = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;
        mPullToRefreshListView = (PullToRefreshListView) findViewById(R.id.list);
        mPullToRefreshListView
                .setMode(PullToRefreshBase.Mode.PULL_UP_TO_REFRESH);
        mPullToRefreshListView
                .setOnRefreshListener(new OnRefreshListener<ListView>() {
                    @Override
                    public void onRefresh(
                            PullToRefreshBase<ListView> refreshView) {
                        String label = DateUtils.formatDateTime(
                                getApplicationContext(),
                                System.currentTimeMillis(),
                                DateUtils.FORMAT_SHOW_TIME
                                        | DateUtils.FORMAT_SHOW_DATE
                                        | DateUtils.FORMAT_ABBREV_ALL);

                        // Update the LastUpdatedLabel
                        refreshView.getLoadingLayoutProxy()
                                .setLastUpdatedLabel(label);

                        mCurrentDate = ZhihuUtils.getBeforeDate(mCurrentDate);
                        new GetMoreNewsTask(mContext).executeOnExecutor(
                                AsyncTask.THREAD_POOL_EXECUTOR, mCurrentDate);
                    }
                });

        ListView actualListView = mPullToRefreshListView.getRefreshableView();
        mAdapter = new NewsAdapter(mContext);
        actualListView.setAdapter(mAdapter);

        new LoadCacheNewsTask()
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        new GetLatestNewsTask(mContext)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private boolean checkIsContentSame(String oldContent, String newContent) {

        if (TextUtils.isEmpty(oldContent) || TextUtils.isEmpty(newContent)) {
            return false;
        }

        return oldContent.equals(newContent);
    }

    // 读取缓存中的最新新闻
    private class LoadCacheNewsTask extends
            AsyncTask<String, Void, NewsListEntity> {

        @Override
        protected NewsListEntity doInBackground(String... params) {

            NewsListEntity latestNewsEntity = ZhihuApplication
                    .getNewsDataSource().getLatestNews();

            if (latestNewsEntity != null) {
                mCurrentDate = latestNewsEntity.date;
            }

            return latestNewsEntity;
        }

        @Override
        protected void onPostExecute(NewsListEntity result) {
            super.onPostExecute(result);

            if (result != null && !ListUtils.isEmpty(result.stories)) {
                NewsEntity tagNewsEntity = new NewsEntity();
                tagNewsEntity.isTag = true;
                tagNewsEntity.title = result.date;
                mAdapter.addDataItem(tagNewsEntity);
                mAdapter.addDataItems(result.stories);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    private class GetLatestNewsTask extends AsyncTask<String, Void, String> {
        Context mContext;

        public GetLatestNewsTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected String doInBackground(String... params) {
            String targetUrl = URL_LATEST;

            ArrayList<NameValuePair> paramList = new ArrayList<NameValuePair>();

            for (int i = 0; i < paramList.size(); i++) {
                NameValuePair nowPair = paramList.get(i);
                String value = nowPair.getValue();
                try {
                    value = URLEncoder.encode(value, "UTF-8");
                } catch (Exception e) {
                }
                if (i == 0) {
                    targetUrl += ("?" + nowPair.getName() + "=" + value);
                } else {
                    targetUrl += ("&" + nowPair.getName() + "=" + value);
                }
            }

            Log.d(TAG, "doInBackground(): targetUrl=" + targetUrl);
            HttpGet httpRequest = new HttpGet(targetUrl);
            try {

                HttpClient httpClient = new DefaultHttpClient();

                HttpResponse httpResponse = httpClient.execute(httpRequest);

                if (httpResponse.getStatusLine().getStatusCode() == 200) {
                    String strResult = EntityUtils.toString(httpResponse
                            .getEntity());
                    return strResult;
                } else {
                    return null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "onPostExecute(): result: " + result);
            String oldContent = null;
            String date = null;
            if (result != null) {

                NewsListEntity newsListEntity = null;
                newsListEntity = (NewsListEntity) GsonUtils.getEntity(result,
                        NewsListEntity.class);
                if (newsListEntity != null) {
                    date = newsListEntity != null ? newsListEntity.date : null;
                    Log.d(TAG, "onPostExecute(): date: " + date);
                    oldContent = ZhihuApplication.getNewsDataSource()
                            .getContent(date);

                    if (!checkIsContentSame(oldContent, result)) {
                        ZhihuApplication.getNewsDataSource()
                                .insertOrUpdateNewsList(
                                        NewsDataSource.NEWS_LIST, date, result);
                    }

                    mCurrentDate = newsListEntity.date;
                    NewsEntity tagNewsEntity = new NewsEntity();
                    tagNewsEntity.isTag = true;
                    tagNewsEntity.title = newsListEntity.date;
                    mAdapter.addDataItem(tagNewsEntity);
                    mAdapter.addDataItems(newsListEntity.stories);
                    mAdapter.notifyDataSetChanged();
                }
            }
        }
    }

    private class GetMoreNewsTask extends AsyncTask<String, Void, String> {
        Context mContext;

        public GetMoreNewsTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected String doInBackground(String... params) {
            if (params.length == 0)
                return null;
            String date = params[0];

            String targetUrl = URL_BEFORE + ZhihuUtils.getAddedDate(date);

            ArrayList<NameValuePair> paramList = new ArrayList<NameValuePair>();

            for (int i = 0; i < paramList.size(); i++) {
                NameValuePair nowPair = paramList.get(i);
                String value = nowPair.getValue();
                try {
                    value = URLEncoder.encode(value, "UTF-8");
                } catch (Exception e) {
                }
                if (i == 0) {
                    targetUrl += ("?" + nowPair.getName() + "=" + value);
                } else {
                    targetUrl += ("&" + nowPair.getName() + "=" + value);
                }
            }

            Log.d(TAG, "doInBackground(): targetUrl=" + targetUrl);
            HttpGet httpRequest = new HttpGet(targetUrl);
            try {

                HttpClient httpClient = new DefaultHttpClient();

                HttpResponse httpResponse = httpClient.execute(httpRequest);

                if (httpResponse.getStatusLine().getStatusCode() == 200) {
                    String strResult = EntityUtils.toString(httpResponse
                            .getEntity());
                    return strResult;
                } else {
                    return null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(TAG, "onPostExecute(): result: " + result);
            String oldContent = null;
            String date = null;
            if (result != null) {

                NewsListEntity newsListEntity = null;
                newsListEntity = (NewsListEntity) GsonUtils.getEntity(result,
                        NewsListEntity.class);
                if (newsListEntity != null) {
                    date = newsListEntity != null ? newsListEntity.date : null;
                    Log.d(TAG, "onPostExecute(): date: " + date);
                    oldContent = ZhihuApplication.getNewsDataSource()
                            .getContent(date);

                    if (!checkIsContentSame(oldContent, result)) {
                        ZhihuApplication.getNewsDataSource()
                                .insertOrUpdateNewsList(
                                        NewsDataSource.NEWS_LIST, date, result);
                    }

                    mCurrentDate = newsListEntity.date;
                    NewsEntity tagNewsEntity = new NewsEntity();
                    tagNewsEntity.isTag = true;
                    tagNewsEntity.title = newsListEntity.date;
                    mAdapter.addDataItem(tagNewsEntity);
                    mAdapter.addDataItems(newsListEntity.stories);
                    mAdapter.notifyDataSetChanged();
                }
            }
            mPullToRefreshListView.onRefreshComplete();
        }
    }

}
