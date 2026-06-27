package io.github.joke.percolate.docs.reactive;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;
import reactor.core.publisher.Flux;

// tag::mapper[]
@Mapper
public interface FeedMapper {

    // A Flux of articles maps element-by-element through `toView`, staying reactive end to end:
    // percolate composes `flux.map(...)` over the element method — it never blocks or collects.
    Flux<ArticleView> map(Flux<Article> articles);

    @Map(target = "headline", source = "article.title")
    ArticleView toView(Article article);
}
// end::mapper[]

// tag::model[]
final class Article {
    private final String title;

    Article(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}

final class ArticleView {
    private final String headline;

    ArticleView(String headline) {
        this.headline = headline;
    }

    public String getHeadline() {
        return headline;
    }
}
// end::model[]
