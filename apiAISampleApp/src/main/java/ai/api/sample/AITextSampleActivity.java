package ai.api.sample;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.api.AIServiceException;
import ai.api.RequestExtras;
import ai.api.android.AIConfiguration;
import ai.api.android.AIDataService;
import ai.api.android.GsonFactory;
import ai.api.model.AIContext;
import ai.api.model.AIError;
import ai.api.model.AIEvent;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Metadata;
import ai.api.model.Result;
import ai.api.model.Status;

/**
 * Created by alexey on 07/12/16.
 */
public class AITextSampleActivity extends BaseActivity implements AdapterView.OnItemSelectedListener, View.OnClickListener {

    public static final String TAG = AITextSampleActivity.class.getName();

    private Gson gson = GsonFactory.getGson();

    private TextView resultTextView;
    private EditText contextEditText;
    private EditText queryEditText;
    private CheckBox eventCheckBox;

    private Spinner eventSpinner;
    private ListView mListView;
    private AIDataService aiDataService;
    ArrayList<String> mArray;
    private  MyAdapter mArrayAdapter;
    ArrayList<String>myask;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_aitext_sample);
        TTS.init(getApplicationContext());

        mArray = new ArrayList<>();
        myask =new ArrayList<>();
        resultTextView = (TextView) findViewById(R.id.resultTextView);
        contextEditText = (EditText) findViewById(R.id.contextEditText);
        queryEditText = (EditText) findViewById(R.id.textQuery);
        mListView = (ListView) findViewById(R.id.listview);
        findViewById(R.id.buttonSend).setOnClickListener(this);
        findViewById(R.id.buttonClear).setOnClickListener(this);
        mArrayAdapter = new MyAdapter(mArray);
        eventSpinner = (Spinner) findViewById(R.id.selectEventSpinner);
        final ArrayAdapter<String> eventAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Config.events);
        eventSpinner.setAdapter(eventAdapter);

        eventCheckBox = (CheckBox) findViewById(R.id.eventsCheckBox);
        checkBoxClicked();
        eventCheckBox.setOnClickListener(this);
        mListView.setAdapter(mArrayAdapter);
        Spinner spinner = (Spinner) findViewById(R.id.selectLanguageSpinner);
        final ArrayAdapter<LanguageConfig> languagesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Config.languages);
        spinner.setAdapter(languagesAdapter);
        spinner.setOnItemSelectedListener(this);
    }

    private void initService(final LanguageConfig selectedLanguage) {
        final AIConfiguration.SupportedLanguages lang = AIConfiguration.SupportedLanguages.fromLanguageTag(selectedLanguage.getLanguageCode());
        final AIConfiguration config = new AIConfiguration(selectedLanguage.getAccessToken(),
                lang,
                AIConfiguration.RecognitionEngine.System);


        aiDataService = new AIDataService(this, config);
    }


    private void clearEditText() {
        queryEditText.setText("");
    }

    /*
    * AIRequest should have query OR event
    */
    private void sendRequest() {

        final String queryString = !eventSpinner.isEnabled() ? String.valueOf(queryEditText.getText()) : null;
        final String eventString = eventSpinner.isEnabled() ? String.valueOf(String.valueOf(eventSpinner.getSelectedItem())) : null;
        final String contextString = String.valueOf(contextEditText.getText());

        if (TextUtils.isEmpty(queryString) && TextUtils.isEmpty(eventString)) {
            onError(new AIError(getString(R.string.non_empty_query)));
            return;
        }
        myask.add(queryString);
        final AsyncTask<String, Void, AIResponse> task = new AsyncTask<String, Void, AIResponse>() {

            private AIError aiError;

            @Override
            protected AIResponse doInBackground(final String... params) {
                final AIRequest request = new AIRequest();
                String query = params[0];
                String event = params[1];

                if (!TextUtils.isEmpty(query))
                    request.setQuery(query);
                if (!TextUtils.isEmpty(event))
                    request.setEvent(new AIEvent(event));
                final String contextString = params[2];
                RequestExtras requestExtras = null;
                if (!TextUtils.isEmpty(contextString)) {
                    final List<AIContext> contexts = Collections.singletonList(new AIContext(contextString));
                    requestExtras = new RequestExtras(contexts, null);
                }

                try {
                    return aiDataService.request(request, requestExtras);
                } catch (final AIServiceException e) {
                    aiError = new AIError(e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(final AIResponse response) {
                if (response != null) {
                    onResult(response);
                } else {
                    onError(aiError);
                }
            }
        };

        task.execute(queryString, eventString, "");
    }

    public void checkBoxClicked() {
        eventSpinner.setEnabled(eventCheckBox.isChecked());
        queryEditText.setVisibility(!eventCheckBox.isChecked() ? View.VISIBLE : View.GONE);
    }


    private void onResult(final AIResponse response) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "onResult");

//                resultTextView.setText(gson.toJson(response));

                Log.i(TAG, "Received success response");
                Log.d(TAG, "run: "+response.toString());
                // this is example how to get different parts of result object
                final Status status = response.getStatus();
                Log.i(TAG, "Status code: " + status.getCode());
                Log.i(TAG, "Status type: " + status.getErrorType());

                final Result result = response.getResult();
                Log.i(TAG, "Resolved query: " + result.getResolvedQuery());

                Log.i(TAG, "Action: " + result.getAction());

                final String speech = result.getFulfillment().getSpeech();
                Log.i(TAG, "Speech: " + speech);
//                resultTextView.setText(speech);
                mArray.add(speech);
                TTS.speak(speech);
                mArrayAdapter.notifyDataSetChanged();
                final Metadata metadata = result.getMetadata();
                if (metadata != null) {
                    Log.i(TAG, "Intent id: " + metadata.getIntentId());
                    Log.i(TAG, "Intent name: " + metadata.getIntentName());
                }

                final HashMap<String, JsonElement> params = result.getParameters();
                if (params != null && !params.isEmpty()) {
                    Log.i(TAG, "Parameters: ");
                    for (final Map.Entry<String, JsonElement> entry : params.entrySet()) {
                        Log.i(TAG, String.format("%s: %s", entry.getKey(), entry.getValue().toString()));
                    }
                }
            }

        });
    }

    private void onError(final AIError error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resultTextView.setText(error.toString());
                Log.d(TAG, "run: "+error.toString());
            }
        });
    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        final LanguageConfig selectedLanguage = (LanguageConfig) parent.getItemAtPosition(position);
        initService(selectedLanguage);
//        new Thread(){
//            @Override
//            public void run() {
//                super.run();
//
//            }
//        }.start();

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonClear:
                clearEditText();
                break;
            case R.id.buttonSend:
                sendRequest();
                break;
            case R.id.eventsCheckBox:
                checkBoxClicked();
                break;
        }
    }
    public  class  MyAdapter extends BaseAdapter{
        private  ArrayList<String> myList;
        public  MyAdapter(ArrayList<String> arrayList){
            myList = arrayList;
        }

        @Override
        public int getCount() {
            return myList == null ? 0: myList.size();
        }

        @Override
        public Object getItem(int position) {
            return myList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = View.inflate(parent.getContext(), R.layout.my_message_item, null);
                viewHolder = new ViewHolder();

                viewHolder.tv_time = (TextView) convertView.findViewById(R.id.id_message_time);
                viewHolder.tv_name_msg = (TextView) convertView.findViewById(R.id.id_message);
                viewHolder.tv_name_msg2 = (TextView) convertView.findViewById(R.id.id_message2);
                viewHolder.iv_img = (ImageView) convertView.findViewById(R.id.id_image_icon);
                convertView.setTag(viewHolder);

            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            viewHolder.tv_name_msg.setText(myList.get(position).toString());
            viewHolder.tv_name_msg2.setText(myask.get(position).toString());
            return convertView;
        }
        class ViewHolder {

            public ImageView iv_img;
            public TextView tv_time;
            public TextView tv_name_msg;
            public TextView tv_name_msg2;
        }
    }
}
