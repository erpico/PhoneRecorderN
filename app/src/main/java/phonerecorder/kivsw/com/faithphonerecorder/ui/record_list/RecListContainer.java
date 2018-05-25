package phonerecorder.kivsw.com.faithphonerecorder.ui.record_list;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;

import com.kivsw.cloud.disk.IDiskIO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import phonerecorder.kivsw.com.faithphonerecorder.model.error_processor.IErrorProcessor;
import phonerecorder.kivsw.com.faithphonerecorder.model.utils.RecordFileNameData;

/**
 * this class holds a dir's content
 */

class RecListContainer {

    private Context appContext;
    private IErrorProcessor errorProcessor;

    private List<RecordListContract.RecordFileInfo> dirContent;
    private List<RecordListContract.RecordFileInfo> visibleDirContent=null;
    private RecListFilter recListFilter;
    private Disposable filterDisposable;
    private boolean hasData;

    private Subject<RecListContainer> contentReadyObservable;
    private int processingCount=0;

    public RecListContainer(Context appContext, IErrorProcessor errorProcessor)
    {
        this.appContext = appContext;
        this.errorProcessor = errorProcessor;
        dirContent=new ArrayList<>();
        visibleDirContent = Collections.emptyList();

        hasData = false;
        contentReadyObservable = PublishSubject.create();

        createFilter();
    }

    protected void createFilter()
    {
        if(filterDisposable!=null)
            filterDisposable.dispose();
        filterDisposable=null;

        recListFilter=new RecListFilter(errorProcessor);
        recListFilter.getObservable()
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<RecordListContract.RecordFileInfo>>() {

                    @Override
                    public void onSubscribe(Disposable d) {
                        filterDisposable = d;
                    }

                    @Override
                    public void onNext(List<RecordListContract.RecordFileInfo> recordFileInfos) {
                        visibleDirContent = recordFileInfos;
                        onChange();
                    }

                    @Override
                    public void onError(Throwable e) {
                        errorProcessor.onError(e);
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }
    public Subject<RecListContainer> getContentReadyObservable()
    {
        return contentReadyObservable;
    };

    private void checkThread()
    {
        if(Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new RuntimeException("Must be main thread");
    }


    public void clean()
    {
        checkThread();
        dirContent.clear();
        visibleDirContent=Collections.emptyList();
        hasData=false;

    }

    private Disposable recordInfoSubscription=null;
    public void setFileList(List<IDiskIO.ResourceInfo> fileList)
    {
        checkThread();
        hasData = true;

        if(recordInfoSubscription!=null && !recordInfoSubscription.isDisposed())
            recordInfoSubscription.dispose();
        recordInfoSubscription=null;

        processingCount=1;
        recListFilter.clearData();
        dirContent = new ArrayList<>(fileList.size());
        onChange();

        emitFilesAsRecordInfo(fileList)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Observer<List<RecordListContract.RecordFileInfo>>() {
            @Override public void onSubscribe(Disposable d) {recordInfoSubscription=d; }

            @Override
            public void onNext(List<RecordListContract.RecordFileInfo> recordFileInfo) {
                dirContent.addAll(recordFileInfo);
                recListFilter.addData(recordFileInfo);
            }

            @Override
            public void onError(Throwable e) {
                errorProcessor.onError(e);
                processingCount--;
                onChange();
            }

            @Override
            public void onComplete() {
                processingCount--;
                onChange();
            }
        });

    }

    protected  void onChange()
    {
         contentReadyObservable.onNext(this);
    }

    public void setFilter(String filter)
    {
        checkThread();
        visibleDirContent=new ArrayList<>(dirContent.size());
        if(filter==null) filter="";

        recListFilter.clearData();
        recListFilter.setFilter(filter);
        recListFilter.addData(dirContent);
    }

    public boolean hasData()
    {
        return hasData;
    }
    public List<RecordListContract.RecordFileInfo> getVisibleDirContent()
    {
        return visibleDirContent;
    }
     public boolean isProcessing()
     {
         return processingCount>0 || recListFilter.isProcessing();
     }

    final static int PACK_SIZE=20;
    protected Observable<List<RecordListContract.RecordFileInfo>> emitFilesAsRecordInfo(final List<IDiskIO.ResourceInfo> fileList)
    {
        Iterator<List<RecordListContract.RecordFileInfo>> iterator= new Iterator<List<RecordListContract.RecordFileInfo>>()
                {
                    private int count=0;
                    private Pattern p;

                    protected void init()
                    {
                        Collections.sort(fileList, new Comparator<IDiskIO.ResourceInfo>() {
                            @Override
                            public int compare(IDiskIO.ResourceInfo o1, IDiskIO.ResourceInfo o2) {
                                return o2.name().compareTo(o1.name());
                            }
                        });
                        p = Pattern.compile(RecordFileNameData.RECORD_PATTERN);//"^[0-9]{8}_[0-9]{6}_"); // this pattern filters the other app's files
                    }

                    @Override
                    public boolean hasNext() {
                        return count < fileList.size();
                    }

                    @Override
                    public List<RecordListContract.RecordFileInfo> next() {
                        if(count==0)
                            init();

                        List<RecordListContract.RecordFileInfo> res = new ArrayList<>(PACK_SIZE);
                        while(count<fileList.size() && res.size()<PACK_SIZE)
                        {
                            IDiskIO.ResourceInfo file = fileList.get(count++);
                            if(!file.isFile()) continue;
                            Matcher m = p.matcher(file.name());
                            if(!m.find()) continue;
                            res.add( getRecordInfo(file.name()) );
                        }
                        return res;
                    }

                };

      return Observable.fromIterable(itarableFromIterator(iterator))
         .subscribeOn(Schedulers.io());

    }

    static<T> Iterable<T> itarableFromIterator(final Iterator<T> i)
    {
        return new Iterable<T>() {
            @NonNull
            @Override
            public Iterator<T> iterator() {
                return i;
            }
        };
    }
    protected RecordListContract.RecordFileInfo getRecordInfo(String fileName)
    {
        RecordListContract.RecordFileInfo item=new RecordListContract.RecordFileInfo();
        item.recordFileNameData = RecordFileNameData.decipherFileName(fileName);
        item. callerName = getNameFromNumber(item.recordFileNameData.phoneNumber);
        return item;
    }


    /** finds and returns name that corresponds phoneNumber
     * @return name or null
     * */
    public String getNameFromNumber(String phoneNumber)
    {
        String res = null;
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            ContentResolver resolver = appContext.getContentResolver();
            Cursor cur = resolver.query(uri, new String[]{ContactsContract.PhoneLookup.NUMBER, ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);

            if (cur != null) {
                if (cur.moveToFirst())
                    res = cur.getString(1);

                if (!cur.isClosed()) cur.close();
            }
        }catch(Exception e)
        {
            e.toString();
        }
        if (res == null) res = "";
        return res;
    }


}