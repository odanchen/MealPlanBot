package app.mealplan.web;
public final class VersionPreconditions {
    private VersionPreconditions() {}
    public static long require(String header) {
        if(header==null || !header.matches("\"(?:0|[1-9][0-9]*)\"")) throw ApiErrors.invalid("If-Match must contain a quoted entity version");
        try { return Long.parseLong(header.substring(1,header.length()-1)); }
        catch(NumberFormatException e) { throw ApiErrors.invalid("Invalid entity version"); }
    }
    public static void check(String header,long current) {
        if(require(header)!=current) throw new ApiErrors.Failure(409,"version_conflict","The resource changed; refresh before retrying",current);
    }
}
