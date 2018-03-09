/*TODO:
* 读取json文件并显示（OK）
* 异步加载图片，更新列表显示
* 根据json中项目的更新日期排序（OK）
* 可增加/删除/长按修改项目，并保存至本地文件（OK）
* 对于B站链接，可根据链接自动获取所需信息（简介，时间，分类等）（OK）
* 可直接在列表上标记观看集数
* 点击打开链接（OK）
* 更新集数提示（用对话框显示，可选择今日不再提示(Neu)/关闭(Posi)）（OK）
* JGit上传/下载数据
*/

package com.lxfly2000.animeschedule;

import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import com.lxfly2000.utilities.AndroidDownloadFileTask;
import com.lxfly2000.utilities.AndroidUtility;
import com.lxfly2000.utilities.FileUtility;
import com.lxfly2000.utilities.YMDDate;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private AnimeJson animeJson;
    private ArrayList<Integer>jsonSortTable;
    private int sortOrder=0;
    ListView listAnime;
    FloatingActionButton fabAnimeUpdate;
    private SharedPreferences preferences;
    private int longPressedListItem=-1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar)findViewById(R.id.toolbar));
        fabAnimeUpdate=(FloatingActionButton)findViewById(R.id.fabShowAnimeUpdate);
        fabAnimeUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                OnShowAnimeUpdate();
            }
        });

        if(!AndroidUtility.CheckPermissionWithFinishOnDenied(this,
                "android.permission.READ_EXTERNAL_STORAGE","No reading permission."))
            return;
        preferences=Values.GetPreference(this);
        if(preferences.getString(Values.keyRepositoryUrl,Values.vDefaultString).contentEquals(Values.vDefaultString)){
            Toast.makeText(this,R.string.message_build_default_settings,Toast.LENGTH_LONG).show();
            Values.BuildDefaultSettings(this);
        }
        listAnime=(ListView)findViewById(R.id.listAnime);
        registerForContextMenu(listAnime);
        listAnime.setOnItemClickListener(listAnimeCallback);
        listAnime.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                longPressedListItem=i;
                return false;
            }
        });
        SaveAndReloadJsonFile(false);
        GetAnimeUpdateInfo(true);

        //注册检查更新广播接收器
        IntentFilter fiUpdate=new IntentFilter();
        fiUpdate.addAction(ACTION_CHECK_UPDATE_RESULT);
        checkUpdateReceiver=new CheckUpdateReceiver();
        registerReceiver(checkUpdateReceiver,fiUpdate);
        //检测更新
        CheckForUpdate(true);
    }

    private AdapterView.OnItemClickListener listAnimeCallback=new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(animeJson.GetWatchUrl(jsonSortTable.get(i)))));
        }
    };

    private void ReadJsonFile(){
        if(!FileUtility.IsFileExists(Values.GetJsonDataFullPath())){
            animeJson=new AnimeJson();
            SaveJsonFile();
        }
        animeJson=new AnimeJson(Values.GetJsonDataFullPath());
    }

    private void SaveJsonFile(){
        animeJson.SaveToFile(Values.GetJsonDataFullPath());
    }

    private void DisplayList(){
        RebuildSortTable(2);
        ArrayList<HashMap<String,Object>>listItems=new ArrayList<>();
        for(int i=0;i<animeJson.GetAnimeCount();i++){
            HashMap<String,Object>listItem=new HashMap<>();
            listItem.put("title",animeJson.GetTitle(jsonSortTable.get(i)));
            listItem.put("description",animeJson.GetDescription(jsonSortTable.get(i)));
            StringBuilder rankingString=new StringBuilder();
            for(int j=0;j<5;j++){
                rankingString.append(j<animeJson.GetRank(jsonSortTable.get(i))?"★":"☆");
            }
            listItem.put("ranking",rankingString.toString());
            StringBuilder strSchedule=new StringBuilder();
            strSchedule.append(animeJson.GetLastUpdateYMDDate(jsonSortTable.get(i)).ToYMDString())
                    .append("更新")
                    .append(animeJson.GetLastUpdateEpisode(jsonSortTable.get(i)))
                    .append("话");
            int haveNotWatched=0;
            for(int j=1;j<=animeJson.GetLastUpdateEpisode(jsonSortTable.get(i));j++){
                if(!animeJson.GetEpisodeWatched(jsonSortTable.get(i),j)){
                    if(haveNotWatched==0)
                        strSchedule.append("，未观看：");
                    else
                        strSchedule.append(", ");
                    haveNotWatched++;
                    strSchedule.append(String.valueOf(j));
                }
            }
            listItem.put("schedule",strSchedule.toString());
            listItem.put("cover", R.mipmap.ic_launcher);
            listItems.add(listItem);
        }
        String[]keyStrings={"title","description","ranking","schedule","cover"};
        int[]viewIds={R.id.textAnimeTitle,R.id.textAnimeDescription,R.id.textRanking,R.id.textSchedule,R.id.imageCover};
        listAnime.setAdapter(new SimpleAdapter(this,listItems,R.layout.item_anime,keyStrings,viewIds));
    }

    //排序，order:0=不排序，1=升序，2=降序
    private void RebuildSortTable(final int order){
        sortOrder=order;
        int listCount=animeJson.GetAnimeCount();
        jsonSortTable=new ArrayList<>(listCount);
        for(int i=0;i<listCount;i++)
            jsonSortTable.add(i);
        if(order==0)
            return;
        jsonSortTable.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                switch (order){
                    case 1:return animeJson.GetLastUpdateYMDDate(a).IsEarlierThanDate(animeJson.GetLastUpdateYMDDate(b))?-1:1;
                    case 2:return animeJson.GetLastUpdateYMDDate(a).IsLaterThanDate(animeJson.GetLastUpdateYMDDate(b))?-1:1;
                }
                return 0;
            }
        });
        int processingTotal=listCount;
        for(int i=0;i<processingTotal;){
            if(animeJson.GetAbandoned(jsonSortTable.get(i))){
                jsonSortTable.add(jsonSortTable.get(i));
                jsonSortTable.remove(i);
                processingTotal--;
            }else{
                i++;
            }
        }
    }

    private void GetAnimeUpdateInfo(boolean onStartup){
        if(onStartup&&preferences.getString(Values.keyAnimeInfoDate,Values.vDefaultString).contentEquals(YMDDate.GetTodayDate().ToYMDString()))
            return;
        StringBuilder msg=new StringBuilder();
        if(animeJson.GetAnimeCount()==0)
            msg.append("无数据\n");
        else
            msg.append("上次观看：").append(animeJson.GetLastWatchDateString()).append(" ").append(animeJson.GetTitle(animeJson.GetLastWatchIndex())).
                    append(" 第").append(animeJson.GetLastWatchEpisode()).append("话\n");
        msg.append("更新信息：");
        int behindCount=0;
        ArrayList<Integer>uTable=new ArrayList<>();
        for(int i=0;i<jsonSortTable.size();i++) {
            if(sortOrder==2)
                uTable.add(i, jsonSortTable.get(jsonSortTable.size() - i - 1));
            else
                uTable.add(i,jsonSortTable.get(i));
        }
        for(int i=0;i<animeJson.GetAnimeCount();i++){
            if(!animeJson.GetAbandoned(uTable.get(i))){
                boolean haveNotWatched=false;
                for(int j=0;j<animeJson.GetLastUpdateEpisode(uTable.get(i));j++){
                    if(!animeJson.GetEpisodeWatched(uTable.get(i), j+1)){
                        haveNotWatched=true;
                        break;
                    }
                }
                if(haveNotWatched) {
                    behindCount++;
                    msg.append("\n").append(animeJson.GetTitle(uTable.get(i))).append(" ").append(animeJson.GetLastUpdateYMDDate(uTable.get(i)).ToYMDString()).
                            append(" 已更新至").append(animeJson.GetLastUpdateEpisode(uTable.get(i))).append("话");
                }
            }
        }
        if(behindCount==0){
            msg.append("你已跟上所有番剧的更新进度。");
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_show_anime_update)
                .setMessage(msg.toString())
                .setPositiveButton(android.R.string.ok,null)
                .setNeutralButton("今日不再提示", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        preferences.edit().putString(Values.keyAnimeInfoDate,YMDDate.GetTodayDate().ToYMDString()).apply();
                    }
                })
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.menu_main,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case R.id.action_settings:OnActionSettings();return true;
            case R.id.action_show_anime_update:OnShowAnimeUpdate();return true;
            case R.id.action_view_web_page:OnViewWebPage();return true;
            case R.id.action_add_item:OnAddAnime();return true;
            case R.id.action_remove_all_item:OnRemoveAllAnime();return true;
            case R.id.action_check_update:CheckForUpdate(false);return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,View v,ContextMenu.ContextMenuInfo menuInfo){
        getMenuInflater().inflate(R.menu.menu_anime_list,menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item){
        switch (item.getItemId()){
            case R.id.action_edit_item:EditAnime(jsonSortTable.get(longPressedListItem),false);break;
            case R.id.action_remove_item:RemoveItem(jsonSortTable.get(longPressedListItem));break;
        }
        return false;
    }

    private void RemoveItem(int index){
        animeJson.RemoveItem(index);
        SaveAndReloadJsonFile(true);
    }

    private void OnAddAnime(){
        int ni=animeJson.AddNewItem();
        jsonSortTable.add(ni);
        EditAnime(ni,true);
    }

    private void OnAddAnimeCallback_Revert(){
        int li=animeJson.GetAnimeCount()-1;
        animeJson.RemoveItem(li);
        jsonSortTable.remove(li);
    }

    private void OnRemoveAllAnime(){
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(R.string.message_notice_title)
                .setMessage(R.string.message_remove_all_warning)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        RemoveAllAnime();
                    }
                })
                .setNegativeButton(android.R.string.no,null)
                .show();
    }

    private EditText editDialogDescription;
    private EditText editDialogCover;
    private EditText editDialogTitle;
    private EditText editDialogStartDate;
    private EditText editDialogUpdatePeriod;
    private Spinner comboDialogUpdatePeriodUnit;
    private EditText editDialogEpisodeCount;
    private EditText editDialogAbsenseCount;
    private EditText editDialogWatchUrl;
    private EditText editDialogWatchedEpisode;
    private EditText editDialogColor;
    private EditText editDialogCategory;
    private CheckBox checkDialogAbandoned;
    private EditText editDialogRanking;
    //TODO：其他对话框中的控件
    private void EditAnime(final int index, final boolean fromAddAction){
        //此处的index已经是对应到JSON的序号了，不用再从排序表里找
        //TODO：完善对话框
        AlertDialog editDialog=new AlertDialog.Builder(this)
                .setTitle(R.string.action_edit_item)
                .setView(R.layout.dialog_edit_anime)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        animeJson.SetDescription(index,editDialogDescription.getText().toString());
                        animeJson.SetCoverUrl(index,editDialogCover.getText().toString());
                        animeJson.SetTitle(index,editDialogTitle.getText().toString());
                        animeJson.SetStartDate(index,editDialogStartDate.getText().toString());
                        animeJson.SetUpdatePeriod(index,Integer.parseInt(editDialogUpdatePeriod.getText().toString()));
                        switch (comboDialogUpdatePeriodUnit.getSelectedItemPosition()){
                            case 0:animeJson.SetUpdatePeriodUnit(index,AnimeJson.unitDay);break;
                            case 1:animeJson.SetUpdatePeriodUnit(index,AnimeJson.unitMonth);break;
                            case 2:animeJson.SetUpdatePeriodUnit(index,AnimeJson.unitYear);break;
                        }
                        animeJson.SetEpisodeCount(index,Integer.parseInt(editDialogEpisodeCount.getText().toString()));
                        animeJson.SetAbsenseCount(index,Integer.parseInt(editDialogAbsenseCount.getText().toString()));
                        animeJson.SetWatchUrl(index,editDialogWatchUrl.getText().toString());
                        String[]strWatchedEpisodeArray=editDialogWatchedEpisode.getText().toString().split(",");
                        for(int i_epi=1;i_epi<=animeJson.GetLastUpdateEpisode(index);i_epi++)
                            animeJson.SetEpisodeWatched(index,i_epi,false);
                        for (String strEachWatched : strWatchedEpisodeArray)
                            if(!strEachWatched.contentEquals(""))
                                animeJson.SetEpisodeWatched(index, Integer.parseInt(strEachWatched), true);
                        animeJson.SetColor(index,editDialogColor.getText().toString());
                        animeJson.SetCategory(index,editDialogCategory.getText().toString().split(","));
                        animeJson.SetAbandoned(index,checkDialogAbandoned.isChecked());
                        animeJson.SetRank(index,Integer.parseInt(editDialogRanking.getText().toString()));
                        Toast.makeText(getBaseContext(),R.string.message_saving_item,Toast.LENGTH_LONG).show();
                        SaveAndReloadJsonFile(true);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(fromAddAction)
                            OnAddAnimeCallback_Revert();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        if(fromAddAction)
                            OnAddAnimeCallback_Revert();
                    }
                })
                .show();
        //http://blog.csdn.net/nihaoqiulinhe/article/details/49026263
        editDialogDescription=(EditText)editDialog.findViewById(R.id.editTextDescription);
        editDialogCover=(EditText)editDialog.findViewById(R.id.editDialogCover);
        editDialogTitle=(EditText)editDialog.findViewById(R.id.editDialogTitle);
        editDialogStartDate=(EditText)editDialog.findViewById(R.id.editDialogStartDate);
        editDialogUpdatePeriod=(EditText)editDialog.findViewById(R.id.editDialogUpdatePeriod);
        comboDialogUpdatePeriodUnit=(Spinner)editDialog.findViewById(R.id.comboDialogUpdatePeriodUnit);
        editDialogEpisodeCount=(EditText)editDialog.findViewById(R.id.editDialogEpisodeCount);
        editDialogAbsenseCount=(EditText)editDialog.findViewById(R.id.editDialogAbsenseCount);
        editDialogWatchUrl=(EditText)editDialog.findViewById(R.id.editDialogWatchUrl);
        editDialogWatchedEpisode=(EditText)editDialog.findViewById(R.id.editDialogWatchedEpisodes);
        editDialogColor=(EditText)editDialog.findViewById(R.id.editDialogColor);
        editDialogCategory=(EditText)editDialog.findViewById(R.id.editDialogCategory);
        checkDialogAbandoned=(CheckBox)editDialog.findViewById(R.id.checkAbandoned);
        editDialogRanking=(EditText)editDialog.findViewById(R.id.editDialogRank);

        editDialogDescription.setText(animeJson.GetDescription(index));
        editDialogCover.setText(animeJson.GetCoverUrl(index));
        editDialogTitle.setText(animeJson.GetTitle(index));
        editDialogStartDate.setText(animeJson.GetStartDate(index));
        editDialogUpdatePeriod.setText(String.valueOf(animeJson.GetUpdatePeriod(index)));
        switch (animeJson.GetUpdatePeriodUnit(index)){
            case AnimeJson.unitDay:comboDialogUpdatePeriodUnit.setSelection(0,true);break;
            case AnimeJson.unitMonth:comboDialogUpdatePeriodUnit.setSelection(1,true);break;
            case AnimeJson.unitYear:comboDialogUpdatePeriodUnit.setSelection(2,true);break;
        }
        editDialogEpisodeCount.setText(String.valueOf(animeJson.GetEpisodeCount(index)));
        editDialogAbsenseCount.setText(String.valueOf(animeJson.GetAbsenseCount(index)));
        editDialogWatchUrl.setText(String.valueOf(animeJson.GetWatchUrl(index)));
        StringBuilder stringBuilder=new StringBuilder();
        for(int i=1;i<=animeJson.GetLastUpdateEpisode(index);i++){
            if(animeJson.GetEpisodeWatched(index,i)){
                if(stringBuilder.length()!=0)
                    stringBuilder.append(",");
                stringBuilder.append(String.valueOf(i));
            }
        }
        editDialogWatchedEpisode.setText(stringBuilder.toString());
        editDialogColor.setText(String.valueOf(animeJson.GetColor(index)));
        String[]strCategoryArray=animeJson.GetCategory(index);
        stringBuilder=new StringBuilder();
        for(int i=0;i<strCategoryArray.length;i++){
            if(i!=0)
                stringBuilder.append(",");
            stringBuilder.append(strCategoryArray[i]);
        }
        editDialogCategory.setText(stringBuilder.toString());
        checkDialogAbandoned.setChecked(animeJson.GetAbandoned(index));
        editDialogRanking.setText(String.valueOf(animeJson.GetRank(index)));
        editDialog.findViewById(R.id.buttonDialogAutofill).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String urlString=editDialogWatchUrl.getText().toString();
                int i_regex=0;
                for(;i_regex<Values.parsableLinksRegex.length;i_regex++){
                    Pattern p=Pattern.compile(Values.parsableLinksRegex[i_regex]);
                    Matcher m=p.matcher(urlString);
                    if(m.find()){
                        if(urlString.toLowerCase().contains("bilibili")){
                            Pattern pSub=Pattern.compile("[0-9]*$");
                            String subFound=urlString.substring(m.start(),m.end());
                            Matcher mSub=pSub.matcher(subFound);
                            if(mSub.find()){
                                ReadBilibiliJsonp_OnCallback(subFound.substring(mSub.start(),mSub.end()));
                                break;
                            }else{
                                throw new IllegalStateException("意外的状态。");
                            }
                        }else if(urlString.toLowerCase().contains("iqiyi")){
                            Toast.makeText(getBaseContext(),"暂不支持读取爱奇艺链接。",Toast.LENGTH_LONG).show();
                            break;
                        }
                    }
                }
                if(i_regex==Values.parsableLinksRegex.length){
                    Toast.makeText(getBaseContext(),"不支持读取该链接。",Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void ReadBilibiliJsonp_OnCallback(String idString){
        String requestUrl="https://bangumi.bilibili.com/jsonp/seasoninfo/"+idString+".ver?callback=seasonListCallback&jsonp=jsonp";
        AndroidDownloadFileTask task=new AndroidDownloadFileTask() {
            @Override
            public void OnReceiveSuccess(String data, Object extra) {
                try {
                    JSONObject biliJson = new JSONObject(data.substring(data.indexOf('{'), data.lastIndexOf('}') + 1));
                    JSONObject biliResult=biliJson.getJSONObject("result");
                    editDialogCover.setText(biliResult.getString("cover"));
                    editDialogTitle.setText(biliResult.getString("bangumi_title"));
                    editDialogDescription.setText(biliResult.getString("evaluate"));
                    editDialogStartDate.setText(biliResult.getString("pub_time").split(" ")[0]);
                    if(biliResult.getString("weekday").contentEquals("-1")){
                        editDialogUpdatePeriod.setText("1");
                        comboDialogUpdatePeriodUnit.setSelection(1,true);
                    }
                    String countString=biliResult.getString("total_count");
                    if(countString.contentEquals("0"))
                        editDialogEpisodeCount.setText("-1");
                    else
                        editDialogEpisodeCount.setText(countString);
                    editDialogRanking.setText(String.valueOf(Math.round(biliResult.getJSONObject("media").getJSONObject("rating").getDouble("score")/2)));
                    StringBuilder tagString=new StringBuilder();
                    for(int i=0;i<biliResult.getJSONArray("tags").length();i++){
                        if(i!=0)
                            tagString.append(",");
                        tagString.append(biliResult.getJSONArray("tags").getJSONObject(i).getString("tag_name"));
                    }
                    editDialogCategory.setText(tagString.toString());
                }catch (JSONException e){
                    Toast.makeText(getBaseContext(),"发生异常：\n"+e.getMessage(),Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void OnReceiveFail(Object extra) {
                Toast.makeText(getBaseContext(),"无法获取番剧信息。",Toast.LENGTH_LONG).show();
            }
        };
        task.execute(requestUrl);
    }

    private void RemoveAllAnime(){
        animeJson.ClearAllAnime();
        SaveAndReloadJsonFile(true);
    }

    private void SaveAndReloadJsonFile(boolean save){
        if(save)
            SaveJsonFile();
        ReadJsonFile();
        DisplayList();
    }

    private void OnShowAnimeUpdate(){
        GetAnimeUpdateInfo(false);
    }

    private void OnViewWebPage(){
        String url=preferences.getString(Values.keyRepositoryUrl,"");
        if(!url.startsWith("https://github.com")){
            AndroidUtility.MessageBox(this,getString(R.string.message_not_supported_url));
            return;
        }
        url=url.substring(19);
        if(url.endsWith(".git"))
            url=url.substring(0,url.length()-4);
        String[]urlParts=url.split("/");
        url="https://"+urlParts[0]+".github.io/"+urlParts[1];
        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
    }

    private void OnActionSettings(){
        startActivityForResult(new Intent(this,SettingsActivity.class),R.id.action_settings&0xFFFF);
    }

    private static final String INTENT_EXTRA_UPDATE_ONLY="onlyUpdate";
    private static final String INTENT_EXTRA_FOUND_UPDATE="foundUpdate";
    private static final String ACTION_CHECK_UPDATE_RESULT=BuildConfig.APPLICATION_ID+".CheckUpdateResult";
    private UpdateChecker updateChecker=null;

    private CheckUpdateReceiver checkUpdateReceiver;
    class CheckUpdateReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean onlyReportNewVersion=intent.getBooleanExtra(INTENT_EXTRA_UPDATE_ONLY,true);
            boolean foundNewVersion=intent.getBooleanExtra(INTENT_EXTRA_FOUND_UPDATE,false);
            AlertDialog.Builder msgBox=new AlertDialog.Builder(context);//这里不能用getApplicationContext.
            msgBox.setPositiveButton(android.R.string.ok,null);
            msgBox.setTitle(R.string.menu_check_update);
            if (foundNewVersion) {
                String msg = String.format(getString(R.string.message_new_version), BuildConfig.VERSION_NAME, updateChecker.GetUpdateVersionName());
                msgBox.setMessage(msg);
                msgBox.setIcon(android.R.drawable.ic_dialog_info);
                msgBox.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent=new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(getString(R.string.url_author)));
                        startActivity(intent);
                    }
                });
                msgBox.setNegativeButton(android.R.string.cancel,null);
            }else if (onlyReportNewVersion){
                return;
            }else if (updateChecker.IsError()){
                msgBox.setMessage(R.string.error_check_update);
                msgBox.setIcon(android.R.drawable.ic_dialog_alert);
            }else {
                msgBox.setMessage(R.string.message_no_update);
            }
            msgBox.show();
        }
    };

    @Override
    protected void onStop(){
        if(checkUpdateReceiver!=null){
            unregisterReceiver(checkUpdateReceiver);
            checkUpdateReceiver=null;
        }
        super.onStop();
    }

    private void CheckForUpdate(boolean onlyReportNewVersion) {
        if (updateChecker == null) {
            updateChecker = new UpdateChecker().SetCheckURL(getString(R.string.url_check_update));
        }
        updateChecker.SetResultHandler(new UpdateChecker.ResultHandler(this) {
            @Override
            protected void OnReceive(boolean foundNewVersion) {
                Intent intent = new Intent(ACTION_CHECK_UPDATE_RESULT);
                intent.putExtra(INTENT_EXTRA_FOUND_UPDATE, foundNewVersion);
                intent.putExtra(INTENT_EXTRA_UPDATE_ONLY, GetOnlyReportUpdate());
                HandlerSendBroadcast(intent);
            }
        }.SetOnlyReportUpdate(onlyReportNewVersion));
        if (checkCallingOrSelfPermission("android.permission.INTERNET") != PackageManager.PERMISSION_GRANTED) {
            if (onlyReportNewVersion)
                return;
            AlertDialog.Builder msgBox = new AlertDialog.Builder(this)
                    .setTitle(R.string.menu_check_update)
                    .setPositiveButton(android.R.string.ok, null)
                    .setMessage(R.string.error_permission_network);
            msgBox.show();
        } else {
            updateChecker.CheckForUpdate();
        }
    }
}
