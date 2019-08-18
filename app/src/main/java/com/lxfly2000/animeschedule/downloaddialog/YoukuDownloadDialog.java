//可直接用链接区分集数；可以选择下载画质
//需要文字说明，不需要钩选框，需要列表框

package com.lxfly2000.animeschedule.downloaddialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.lxfly2000.animeschedule.AnimeJson;
import com.lxfly2000.animeschedule.R;

public class YoukuDownloadDialog extends DownloadDialog {
    public YoukuDownloadDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    public void OpenDownloadDialog(AnimeJson json, int index) {
        AlertDialog dialog=new AlertDialog.Builder(ctx)
                .setTitle(json.GetTitle(index))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        AlertDialog dq=new AlertDialog.Builder(ctx)
                                .setTitle(json.GetTitle(index))
                                .setView(R.layout.dialog_anime_download_choose_quality)
                                .setPositiveButton(android.R.string.ok,null)
                                .show();
                    }
                })
                .setView(R.layout.dialog_anime_download_with_notice)
                .show();
        ((TextView)dialog.findViewById(R.id.textViewDownloadNotice)).setText(R.string.label_youku_download_notice);
    }
}
