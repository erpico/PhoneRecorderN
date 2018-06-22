package com.kivsw.phonerecorder.model;

import android.content.Context;

import com.kivsw.cloud.DiskContainer;
import com.kivsw.cloud.disk.IDiskRepresenter;
import com.kivsw.cloud.disk.StorageUtils;
import com.kivsw.cloud.disk.localdisk.LocalDiskRepresenter;
import com.kivsw.cloud.disk.pcloud.PcloudRepresenter;
import com.kivsw.cloud.disk.yandex.YandexRepresenter;
import com.kivsw.phonerecorder.model.keys.keys;

import java.util.ArrayList;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * this DI module provides the list of available disks
 */
@Module
public class DiskRepresentativeModule {

    @Provides
    @Singleton
    public DiskContainer provideDisks(Context context)
    {
        ArrayList<IDiskRepresenter> disks;

        disks = new ArrayList();
        disks.addAll(StorageUtils.getSD_list(context));
        disks.add(LocalDiskRepresenter.createPrivateStorageFS(context));

        disks.add(new PcloudRepresenter(context, keys.getYPCloudKey()));
        disks.add(new YandexRepresenter(context, keys.getYandexDiskKey(), null, null));

        return new DiskContainer(disks);
    }

}