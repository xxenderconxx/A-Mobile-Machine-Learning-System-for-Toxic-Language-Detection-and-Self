package com.example.toxiclanguagekeyboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private Button btnNext;
    private Button btnSkip;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("ToxicKeyboardPrefs", MODE_PRIVATE);
        if (prefs.getBoolean("has_completed_tutorial", false)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        
        setContentView(R.layout.activity_onboarding);
        
        viewPager = findViewById(R.id.viewPager);
        btnNext = findViewById(R.id.btnNext);
        btnSkip = findViewById(R.id.btnSkip);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        List<OnboardingPage> pages = new ArrayList<>();
        pages.add(new OnboardingPage(getString(R.string.onboarding_title_1), getString(R.string.onboarding_desc_1), R.mipmap.ic_launcher_logo));
        pages.add(new OnboardingPage(getString(R.string.onboarding_title_2), getString(R.string.onboarding_desc_2), R.mipmap.ic_launcher_logo));
        pages.add(new OnboardingPage(getString(R.string.onboarding_title_3), getString(R.string.onboarding_desc_3), R.mipmap.ic_launcher_logo));
        pages.add(new OnboardingPage(getString(R.string.onboarding_title_4), getString(R.string.onboarding_desc_4), R.mipmap.ic_launcher_logo));

        OnboardingAdapter adapter = new OnboardingAdapter(pages);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {}).attach();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == pages.size() - 1) {
                    btnNext.setText(R.string.btn_get_started);
                } else {
                    btnNext.setText(R.string.btn_next);
                }
            }
        });

        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() < pages.size() - 1) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                finishOnboarding();
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());
    }

    private void finishOnboarding() {
        prefs.edit().putBoolean("has_completed_tutorial", true).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private static class OnboardingPage {
        String title;
        String description;
        int imageRes;

        OnboardingPage(String title, String description, int imageRes) {
            this.title = title;
            this.description = description;
            this.imageRes = imageRes;
        }
    }

    private static class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {
        private final List<OnboardingPage> pages;

        OnboardingAdapter(List<OnboardingPage> pages) {
            this.pages = pages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_page, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OnboardingPage page = pages.get(position);
            holder.tvTitle.setText(page.title);
            holder.tvDescription.setText(page.description);
            holder.ivIllustration.setImageResource(page.imageRes);
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIllustration;
            TextView tvTitle;
            TextView tvDescription;

            ViewHolder(View itemView) {
                super(itemView);
                ivIllustration = itemView.findViewById(R.id.ivIllustration);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvDescription = itemView.findViewById(R.id.tvDescription);
            }
        }
    }
}