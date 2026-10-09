from pathlib import Path
import csv, hashlib, json, subprocess, tempfile, math
import pandas as pd
from sklearn.metrics import confusion_matrix

output_dir=Path(__file__).resolve().parent
repo=output_dir.parent
project=repo/'ToxicLanguageKeyboard'
work=repo/'ToxicDetectionML'
test=work/'test.csv'; label_file=work/'test_labels.csv'
labels=pd.read_csv(label_file)
data=pd.read_csv(test).merge(labels,on='id',validate='one_to_one')
data=data[data.toxic!=-1].copy()
label_columns=['toxic','severe_toxic','obscene','threat','insult','identity_hate']
data['expected']=data[label_columns].sum(axis=1).gt(0).astype(int)
assert len(data)==63978

main_classes=project/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
test_classes=project/'app/build/intermediates/javac/debugUnitTest/compileDebugUnitTest/classes'
if not test_classes.exists():
    matches=list((project/'app/build/intermediates').rglob('RevisionEvaluator.class'))
    if not matches: raise FileNotFoundError('Build the unit-test classes first')
    test_classes=matches[0].parents[3]
org_json=Path(r'C:\Users\xxend\.gradle\caches\modules-2\files-2.1\org.json\json\20231013\e22e0c040fe16f04ffdb85d851d77b07fc05ea52\json-20231013.jar')
android=Path(r'C:\Users\xxend\AppData\Local\Android\Sdk\platforms\android-34\android.jar')
java=Path(r'C:\Users\xxend\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2\bin\java.exe')
for path in [main_classes,test_classes,org_json,android,java]:
    if not path.exists(): raise FileNotFoundError(str(path))
classpath=';'.join(map(str,[test_classes,main_classes,org_json,android]))
asset=project/'app/src/main/assets'
out=repo/'revision-evidence'

with tempfile.TemporaryDirectory(prefix='revision_pipeline_') as temp:
    temp=Path(temp); input_file=temp/'test-input.jsonl'; output_file=temp/'java-results.jsonl'
    with input_file.open('w',encoding='utf-8',newline='\n') as stream:
        for record in data[['id','comment_text']].itertuples(index=False,name=None):
            stream.write(json.dumps({'id':str(record[0]),'text':'' if pd.isna(record[1]) else str(record[1])},ensure_ascii=False)+'\n')
    try:
        completed=subprocess.run([str(java),'-Xmx2g','-cp',classpath,
        'com.example.toxiclanguagekeyboard.RevisionFullPipelineEvaluator',
        str(asset/'model_weights.json'),str(asset/'substitutions.json'),str(input_file),str(output_file)],
            check=True,text=True,capture_output=True)
    except subprocess.CalledProcessError as error:
        raise RuntimeError(error.stderr) from error
    (out/'full-pipeline-run.log').write_text(completed.stderr,encoding='utf-8')
    predictions=[json.loads(line) for line in output_file.read_text(encoding='utf-8').splitlines()]

assert len(predictions)==len(data)==63978
assert [str(row['id']) for row in predictions]==[str(value) for value in data.id]
y=data.expected.to_numpy()
def metrics(pred):
    tn,fp,fn,tp=map(int,confusion_matrix(y,pred,labels=[0,1]).ravel())
    return {'tn':tn,'fp':fp,'fn':fn,'tp':tp,'fpr':fp/(fp+tn),'accuracy':(tn+tp)/len(y),
            'precision':tp/(tp+fp) if tp+fp else 0,'recall':tp/(tp+fn) if tp+fn else 0}
classifier=[int(row['classifier']) for row in predictions]
rule=[int(row['rules'] and not row['suppressed']) for row in predictions]
combined=[int(row['combined']) for row in predictions]
report={'dataset':'test.csv merged one-to-one with test_labels.csv, excluding records where toxic=-1',
        'row_count':len(data),'positive':int(y.sum()),'negative':int(len(y)-y.sum()),
        'target':'any of toxic, severe_toxic, obscene, threat, insult, identity_hate equals 1',
        'model_sha256':hashlib.sha256((asset/'model_weights.json').read_bytes()).hexdigest(),
        'rules_sha256':hashlib.sha256((asset/'substitutions.json').read_bytes()).hexdigest(),
        'threshold':0.78,'threshold_selected_on':'training script validation set, before test evaluation',
        'pipeline':'Production Java ToxicityClassifier plus SubstitutionEngine explicit rules, DetectionPipeline suppression semantics; no personal false-positive overrides or professional-context adjustment.',
        'classifier_only':metrics(classifier),'explicit_rules_with_phrase_suppression':metrics(rule),
        'combined_classifier_and_rules':metrics(combined),
        'interpretation':'Full test-set estimate of revised-version classifier/rules logic; not the unavailable expert 3,000-sample replay, and not live keyboard UI or human expert evaluation.'}
out.mkdir(exist_ok=True)
(out/'full-pipeline-test-evaluation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
with (out/'full-pipeline-test-predictions.csv').open('w',encoding='utf-8',newline='') as stream:
    writer=csv.writer(stream); writer.writerow(['test_record_id','expected_toxic','classifier_prediction','explicit_rule_prediction','phrase_suppressed','combined_prediction'])
    for expected,pred in zip(y,predictions): writer.writerow([pred['id'],int(expected),int(pred['classifier']),int(pred['rules']),int(pred['suppressed']),int(pred['combined'])])
print(json.dumps(report,indent=2))

