cd ..

python eval/evaluator.py \
--answer_dir=./results/gpt-pass10-temp-1/ \
--pattern-path=./eval/pat_list.txt \
--dataset_path=./dataset/question.jsonl \
--model=gpt \
--passk=10

rm .tmp*