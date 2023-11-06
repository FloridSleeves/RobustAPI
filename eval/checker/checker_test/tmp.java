public class AlarmHelper {
    private Activity activity;

    public AlarmHelper(Activity activity) {
        this.activity = activity;
    }

    public void setupDatePicker() {
        long maxTime = 52560000;
        final Calendar cal = Calendar.getInstance();
        DatePicker dp = (DatePicker) activity.findViewById(R.id.datePicker);
        dp.setMinDate(cal.getTimeInMillis() - 1000);
        dp.setMaxDate(cal.getTimeInMillis() + maxTime);
    }

    }

protected void onCreate(Bundle savedInstanceState) {     super.onCreate(savedInstanceState);     requestWindowFeature(Window.FEATURE_NO_TITLE);     getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);     AlarmHelper helper = new AlarmHelper(this);     helper.setupDatePicker();     setContentView(R.layout.activity_main); }