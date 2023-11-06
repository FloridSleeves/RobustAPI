cd ..

python eval/evaluator.py \
--answer_dir=./results/gpt3.5/one_shot/ \
--pattern-path=./eval/pat_list.txt \
--dataset_path=./dataset/question.jsonl \
--model=gpt \
--passk=1
