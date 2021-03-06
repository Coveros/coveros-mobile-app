package com.coveros.coverosmobileapp.blogpost;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.coveros.coverosmobileapp.R;
import com.coveros.coverosmobileapp.dialog.AlertDialogFactory;
import com.coveros.coverosmobileapp.errorlistener.NetworkErrorListener;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays comments for a blog post.
 *
 * @author Maria Kim
 */
@SuppressWarnings("squid:MaximumInheritanceDepth")
public class CommentsListActivity extends BlogListActivity {

    private RequestQueue rQueue;
    private ListView commentsListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.comment_list);

        commentsListView = getListView();

        commentsListView.addHeaderView(createTextViewLabel(CommentsListActivity.this, getResources().getString(R.string.comments_label)));  // setting label above comments list

        final String postId = getIntent().getExtras().getString("postId");

        final String networkErrorAlertDialogMessage = getString(R.string.comments_network_error_message);
        networkErrorAlertDialog = AlertDialogFactory.createNetworkErrorAlertDialogDefaultButton(CommentsListActivity.this, networkErrorAlertDialogMessage);
        networkErrorListener = new NetworkErrorListener(CommentsListActivity.this, networkErrorAlertDialog);

        final String commentsUrl = "http://www3.dev.secureci.com/wp-json/wp/v2/comments?post=" + postId;

        Thread commentRequest = new Thread() {
            @Override
            public void run() {
                rQueue = Volley.newRequestQueue(CommentsListActivity.this);
                retrieveComments(new PostReadCallback<Comment>() {
                    @Override
                    public void onSuccess(List<Comment> newComments) {
                        if (newComments.isEmpty()) {
                            JsonObject noCommentsJson = (JsonObject) new JsonParser().parse("{\"author_name\": \"\", \"date\": \"\", \"content\": {\"rendered\": \"<p>No comments to display.</p>\"}}");
                            newComments.add(new Comment(noCommentsJson));
                        }
                        CommentsListAdapter commentsAdapter = new CommentsListAdapter(CommentsListActivity.this, R.layout.comment_list_text, newComments);
                        commentsListView.setAdapter(commentsAdapter);
                    }
                }, commentsUrl);
            }
        };
        commentRequest.start();

        // when "Leave a Comment" button is clicked, will open comment form
        Button openCommentFormButton = (Button) findViewById(R.id.leave_comment);
        openCommentFormButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), CommentFormActivity.class);
                intent.putExtra("postId", postId);
                startActivity(intent);
            }
        });
    }

    /**
     * Passes List of Comments for the BlogPost from Wordpress to callback.
     *
     * @param postReadCallback A callback function to be executed after the list of authors has been retrieved
     */
    protected void retrieveComments(final PostReadCallback<Comment> postReadCallback, String commentsUrl) {
        StringRequest commentsRequest = new StringRequest(Request.Method.GET, commentsUrl, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                List<Comment> comments = new ArrayList<>();
                JsonArray commentsJson = new JsonParser().parse(response).getAsJsonArray();
                for (JsonElement comment : commentsJson) {
                    comments.add(new Comment((JsonObject) comment));
                }
                postReadCallback.onSuccess(comments);
            }
        }, networkErrorListener);
        rQueue.add(commentsRequest);
    }

    /**
     * Used to ensure StringRequests are completed before their data are used.
     */
    interface PostReadCallback<T> {
        void onSuccess(List<T> newItems);
    }

    public ListView getCommentsListView() {
        return commentsListView;
    }

}
