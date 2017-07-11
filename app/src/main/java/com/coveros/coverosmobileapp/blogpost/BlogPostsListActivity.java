package com.coveros.coverosmobileapp.blogpost;


import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;

import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.coveros.coverosmobileapp.R;
import com.coveros.coverosmobileapp.website.MainActivity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Maria Kim
 *         Creates ListView that displays list of titles of blog post_list.
 */
public class BlogPostsListActivity extends BlogListActivity {

    private List<BlogPost> blogPosts = new ArrayList<>();
    private SparseArray<String> authors = new SparseArray<>();  // to aggregate the ids and names of the authors of displayed blog posts
    private RequestQueue rQueue;

    private static final String[] MENU_TITLES = new String[]{"Home", "Blog", "Bookmarks"};
    private DrawerLayout menu;
    private ListView drawerList;
    private LinearLayout postList;

    private ListView blogPostsListView;

    private BlogPostsListAdapter postsAdapter;

    private int currentListSize;

    private static final int POSTS_PER_PAGE = 10;
    private int postsOffset = 0;

    private static final int NUM_OF_AUTHORS = 100;  // number of users that will be returned by the REST call... so if someday Coveros has over 100 employees, this needs to be changed
    private static final String AUTHORS_URL = "https://www3.dev.secureci.com/wp-json/wp/v2/users?orderby=id&per_page=" + NUM_OF_AUTHORS;

    private static final String POSTS_URL = "https://www3.dev.secureci.com/wp-json/wp/v2/posts?per_page=" + POSTS_PER_PAGE + "&order=desc&orderby=date&fields=id,title,date,author&offset=%d";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.post_list);

        blogPostsListView = getListView();
        blogPostsListView.addHeaderView(createTextViewLabel(BlogPostsListActivity.this, getResources().getString(R.string.blogposts_label)));  // settings label above blog post list

        errorListener = createErrorListener(BlogPostsListActivity.this);

        postList = (LinearLayout) findViewById(R.id.postlist);
        menu = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerList = (ListView) findViewById(R.id.left_drawer);
        drawerList.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, MENU_TITLES));
        drawerList.setOnItemClickListener(new BlogPostsListActivity.DrawerItemClickListener());
        menu.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                                   @Override
                                   public void onDrawerSlide(View drawerView, float slideOffset) {
                                       postList.setTranslationX(slideOffset * drawerView.getWidth());
                                       menu.bringChildToFront(drawerView);
                                       menu.requestLayout();
                                   }
                               }
        );

        // running these requests on a separate thread for performance
        Thread requestsThread = new RequestsThread();
        requestsThread.start();

        // when a post is selected, feeds its associated data into a BlogPostReadActivity activity
        blogPostsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BlogPost blogPost = blogPosts.get(position - 1);  // -1 because the TextView offsets the blogPosts by one for some reason
                ArrayList<String> blogPostData = new ArrayList<>();
                blogPostData.add(String.valueOf(blogPost.getId()));
                blogPostData.add(blogPost.getTitle());
                blogPostData.add(blogPost.getContent());
                Intent intent = new Intent(getApplicationContext(), BlogPostReadActivity.class);
                intent.putStringArrayListExtra("postData", blogPostData);
                startActivity(intent);
            }
        });
    }

    /**
     * Adds blogPosts to the list view. Called when user scrolls to the bottom of the listview.
     */
    protected void addPosts() {
        Thread addPostRequest = new Thread() {
            @Override
            public void run() {
                retrieveBlogPosts(new PostListCallback<BlogPost>() {
                    @Override
                    public void onSuccess(List<BlogPost> newPosts) {
                        postsAdapter.addAll(newPosts);
                        postsAdapter.notifyDataSetChanged();
                    }
                });
            }
        };
        addPostRequest.start();
    }

    Response.ErrorListener getErrorListener() {
        return errorListener;
    }

    /**
     * Custom thread to run requests for post and author data.
     */
    class RequestsThread extends Thread {
        @Override
        public void run() {
            rQueue = Volley.newRequestQueue(BlogPostsListActivity.this);
            retrieveAuthors(new PostListCallback<String>() {
                @Override
                public void onSuccess(List<String> newAuthors) {
                    retrieveBlogPosts(new PostListCallback<BlogPost>() {
                        @Override
                        public void onSuccess(List<BlogPost> newPosts) {
                            blogPosts.addAll(newPosts);
                            postsAdapter = new BlogPostsListAdapter(BlogPostsListActivity.this, R.layout.post_list_text, blogPosts);
                            blogPostsListView.setAdapter(postsAdapter);
                            currentListSize = blogPostsListView.getAdapter().getCount();
                            blogPostsListView.setOnScrollListener(new BlogPostsListOnScrollListener());
                        }

                    });
                }
            });
        }
    }

    /**
     * Populates List of Authors.
     *
     * @param postListCallback A callback function to be executed after the list of authors has been retrieved
     */
    protected void retrieveAuthors(final PostListCallback<String> postListCallback) {
        StringRequest authorsRequest = new StringRequest(Request.Method.GET, AUTHORS_URL, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                JsonArray authorsJson = new JsonParser().parse(response).getAsJsonArray();
                for (JsonElement author : authorsJson) {
                    JsonObject authorJson = (JsonObject) author;
                    Integer id = authorJson.get("id").getAsInt();
                    authors.put(id, authorJson.get("name").getAsString());
                }
                postListCallback.onSuccess(null);
            }
        }, errorListener);
        rQueue.add(authorsRequest);
    }

    /**
     * Populates List of Posts.
     *
     * @param postListCallback A callback function to be executed after the list of blogPosts has been retrieved
     */
    protected void retrieveBlogPosts(final PostListCallback<BlogPost> postListCallback) {
        StringRequest blogPostsRequest = new StringRequest(Request.Method.GET, String.format(Locale.US, POSTS_URL, postsOffset), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                JsonArray blogPostsJson = new JsonParser().parse(response).getAsJsonArray();
                List<BlogPost> newBlogPosts = new ArrayList<>();
                for (JsonElement blogPost : blogPostsJson) {
                    newBlogPosts.add(new BlogPost((JsonObject) blogPost, authors));
                }
                postListCallback.onSuccess(newBlogPosts);
                postsOffset = postsOffset + POSTS_PER_PAGE;
            }
        }, errorListener);

        rQueue.add(blogPostsRequest);
    }

    /**
     * Used to ensure StringRequests are completed before their data are used.
     */
    interface PostListCallback<T> {
        void onSuccess(List<T> newItems);
    }

    /**
     * Custom OnScrollListener for blogPostsList.
     */
    class BlogPostsListOnScrollListener implements AbsListView.OnScrollListener {
        private boolean firstScroll = true;  // first time scrolling to bottom

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            // no need to listen to the scroll while scrolling. just need the final position of the scroll (onScroll())
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            boolean isScrolledToBottom = firstVisibleItem + visibleItemCount == totalItemCount && totalItemCount != 0; // ListView is scrolled to bottom
            if (BlogPostsListActivity.this.blogPostsListView.getAdapter() != null && isScrolledToBottom) {
                // ensures new blogPosts are loaded only once per time the bottom is reached (i.e. if the user continuously scrolls to the bottom, more than "POSTS_PER_PAGE" blogPosts will not be loaded
                if (firstScroll) {
                    addPosts();
                    firstScroll = false;
                } else if (BlogPostsListActivity.this.blogPostsListView.getAdapter().getCount() == currentListSize + POSTS_PER_PAGE) {
                    addPosts();
                    currentListSize = BlogPostsListActivity.this.blogPostsListView.getAdapter().getCount();
                }
            }
        }
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (position == 0) {
                startActivity(new Intent(getApplicationContext(), MainActivity.class));
            } else {

                startActivity(new Intent(getApplicationContext(), BlogPostsListActivity.class));
            }
        }
    }

    public ListView getBlogPostsListView() {
        return blogPostsListView;
    }
}


